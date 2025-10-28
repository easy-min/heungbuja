"""
음성 명령 멀티태스크 모델 학습
Intent Classification + NER (Named Entity Recognition)
"""

import pandas as pd
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset as TorchDataset
from transformers import (
    AutoTokenizer,
    AutoModel,
    Trainer,
    TrainingArguments,
    EarlyStoppingCallback
)
from datasets import Dataset
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score, classification_report
import os
import json

# GPU 설정
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"🖥️  Using device: {device}")

# WandB 비활성화
os.environ["WANDB_DISABLED"] = "true"

# ============================================
# 1. 태그 및 Intent 정의
# ============================================
NER_TAGS = ['O', 'B-SONG', 'I-SONG', 'B-ARTIST', 'I-ARTIST']
ner_tag2id = {tag: idx for idx, tag in enumerate(NER_TAGS)}
ner_id2tag = {idx: tag for tag, idx in ner_tag2id.items()}

INTENTS = [
    'SELECT_BY_ARTIST',
    'SELECT_BY_TITLE',
    'SELECT_BY_ARTIST_TITLE',
    'PAUSE',
    'RESUME',
    'NEXT_SONG',
    'STOP',
    'START_LISTENING',
    'START_EXERCISE',
    'SWITCH_TO_EXERCISE',
    'SWITCH_TO_LISTENING',
    'EMERGENCY'
]
intent2id = {intent: idx for idx, intent in enumerate(INTENTS)}
id2intent = {idx: intent for intent, idx in intent2id.items()}

print(f"\n🏷️  태그 정의:")
print(f"   NER Tags: {NER_TAGS}")
print(f"   Intents: {len(INTENTS)}개")

# ============================================
# 2. 멀티태스크 모델 정의
# ============================================
class VoiceCommandModel(nn.Module):
    """
    음성 명령 멀티태스크 모델
    - Intent Classification (문장 레벨)
    - NER (토큰 레벨)
    """
    def __init__(self, model_name, num_ner_tags, num_intents):
        super().__init__()
        self.backbone = AutoModel.from_pretrained(model_name)
        self.dropout = nn.Dropout(0.1)
        
        # NER Head (토큰별 분류)
        self.ner_classifier = nn.Linear(768, num_ner_tags)
        
        # Intent Head (문장 전체 분류)
        self.intent_classifier = nn.Linear(768, num_intents)
        
        self.num_ner_tags = num_ner_tags
        self.num_intents = num_intents
    
    def forward(self, input_ids, attention_mask, ner_labels=None, intent_labels=None):
        outputs = self.backbone(input_ids=input_ids, attention_mask=attention_mask)
        
        sequence_output = self.dropout(outputs.last_hidden_state)  # [batch, seq_len, 768]
        pooled_output = self.dropout(outputs.pooler_output)        # [batch, 768]
        
        # NER 예측
        ner_logits = self.ner_classifier(sequence_output)  # [batch, seq_len, num_ner_tags]
        
        # Intent 예측
        intent_logits = self.intent_classifier(pooled_output)  # [batch, num_intents]
        
        total_loss = None
        if ner_labels is not None and intent_labels is not None:
            loss_fct = nn.CrossEntropyLoss()
            
            # NER Loss (패딩 무시)
            active_loss = attention_mask.view(-1) == 1
            active_logits = ner_logits.view(-1, self.num_ner_tags)
            active_labels = torch.where(
                active_loss,
                ner_labels.view(-1),
                torch.tensor(loss_fct.ignore_index).type_as(ner_labels)
            )
            ner_loss = loss_fct(active_logits, active_labels)
            
            # Intent Loss
            intent_loss = loss_fct(intent_logits, intent_labels)
            
            # 가중 합산 (NER: 0.4, Intent: 0.6)
            total_loss = 0.4 * ner_loss + 0.6 * intent_loss
        
        return {
            'loss': total_loss,
            'ner_logits': ner_logits,
            'intent_logits': intent_logits
        }

# ============================================
# 3. 데이터 로드 및 전처리
# ============================================
print("\n📂 데이터 로딩...")
df = pd.read_csv('/mnt/user-data/outputs/augmented_voice_commands.csv')
print(f"✅ 총 {len(df):,}개 샘플 로드")

print(f"\n📊 Intent 분포:")
print(df['intent'].value_counts())

# ============================================
# 4. 토큰화 및 레이블 정렬
# ============================================
print("\n🔤 토크나이저 로딩...")
model_name = "klue/roberta-base"
tokenizer = AutoTokenizer.from_pretrained(model_name)
print(f"✅ {model_name} 토크나이저 로드 완료")

def tokenize_and_align_labels(examples):
    """
    토큰화와 NER 레이블 정렬 + Intent 레이블 추가
    """
    all_input_ids = []
    all_attention_masks = []
    all_ner_labels = []
    all_intent_labels = []
    
    for idx in range(len(examples['text'])):
        text = examples['text'][idx]
        intent = examples['intent'][idx]
        song = examples['song'][idx]
        artist = examples['artist'][idx]
        song_start = examples['song_start'][idx]
        song_end = examples['song_end'][idx]
        artist_start = examples['artist_start'][idx]
        artist_end = examples['artist_end'][idx]
        
        # 토큰화
        encoded = tokenizer(
            text,
            truncation=True,
            max_length=128,
            padding='max_length',
            return_offsets_mapping=True,
        )
        
        input_ids = encoded["input_ids"]
        attention_mask = encoded["attention_mask"]
        offsets = encoded["offset_mapping"]
        
        # Intent 레이블
        intent_label = intent2id[intent]
        
        # NER 레이블 생성
        ner_labels = []
        prev_label = "O"
        
        for i, (start, end) in enumerate(offsets):
            # 패딩 또는 특수 토큰
            if attention_mask[i] == 0 or (start == 0 and end == 0):
                ner_labels.append(-100)
                prev_label = "O"
                continue
            
            token_start = start
            
            # 곡명 영역
            if song_start != -1 and song_start <= token_start < song_end:
                if prev_label != "SONG":
                    ner_labels.append(ner_tag2id["B-SONG"])
                    prev_label = "SONG"
                else:
                    ner_labels.append(ner_tag2id["I-SONG"])
            
            # 가수 영역
            elif artist_start != -1 and artist_start <= token_start < artist_end:
                if prev_label != "ARTIST":
                    ner_labels.append(ner_tag2id["B-ARTIST"])
                    prev_label = "ARTIST"
                else:
                    ner_labels.append(ner_tag2id["I-ARTIST"])
            
            # 나머지
            else:
                ner_labels.append(ner_tag2id["O"])
                prev_label = "O"
        
        all_input_ids.append(input_ids)
        all_attention_masks.append(attention_mask)
        all_ner_labels.append(ner_labels)
        all_intent_labels.append(intent_label)
    
    return {
        "input_ids": all_input_ids,
        "attention_mask": all_attention_masks,
        "ner_labels": all_ner_labels,
        "intent_labels": all_intent_labels,
    }

# ============================================
# 5. Dataset 생성
# ============================================
print("\n🔨 데이터셋 토큰화 중...")
dataset = Dataset.from_pandas(df)

tokenized_dataset = dataset.map(
    tokenize_and_align_labels,
    batched=True,
    remove_columns=dataset.column_names,
    desc="토큰화 진행 중"
)

print("✅ 토큰화 완료")

# 샘플 확인
print("\n📝 토큰화 샘플:")
sample = tokenized_dataset[0]
print(f"Intent Label: {sample['intent_labels']} ({id2intent[sample['intent_labels']]})")
tokens = tokenizer.convert_ids_to_tokens(sample['input_ids'])
ner_labels = [ner_id2tag.get(l, 'PAD') if l != -100 else 'IGN' for l in sample['ner_labels']]
print(f"\n{'토큰':<20} | NER 태그")
print("-" * 40)
for i, (token, label) in enumerate(zip(tokens[:20], ner_labels[:20])):
    if token != tokenizer.pad_token:
        print(f"{token:<20} | {label}")

# ============================================
# 6. Train/Validation 분할
# ============================================
print("\n✂️  데이터 분할 중...")
train_test = tokenized_dataset.train_test_split(test_size=0.15, seed=42)
train_dataset = train_test['train']
eval_dataset = train_test['test']

print(f"✅ 분할 완료")
print(f"   - Train: {len(train_dataset):,}개")
print(f"   - Validation: {len(eval_dataset):,}개")

# ============================================
# 7. 모델 초기화
# ============================================
print(f"\n🤖 모델 초기화... ({model_name})")
model = VoiceCommandModel(
    model_name=model_name,
    num_ner_tags=len(NER_TAGS),
    num_intents=len(INTENTS)
)
model.to(device)
print("✅ 모델 초기화 완료")

# ============================================
# 8. Custom Trainer
# ============================================
class MultiTaskTrainer(Trainer):
    """
    멀티태스크 학습을 위한 커스텀 Trainer
    """
    def compute_loss(self, model, inputs, return_outputs=False, num_items_in_batch=None):
        outputs = model(
            input_ids=inputs['input_ids'],
            attention_mask=inputs['attention_mask'],
            ner_labels=inputs['ner_labels'],
            intent_labels=inputs['intent_labels']
        )
        loss = outputs['loss']
        return (loss, outputs) if return_outputs else loss
    
    def prediction_step(self, model, inputs, prediction_loss_only, ignore_keys=None):
        inputs = self._prepare_inputs(inputs)
        
        with torch.no_grad():
            outputs = model(
                input_ids=inputs['input_ids'],
                attention_mask=inputs['attention_mask'],
                ner_labels=inputs.get('ner_labels'),
                intent_labels=inputs.get('intent_labels')
            )
            loss = outputs['loss']
            
        if prediction_loss_only:
            return (loss, None, None)
        
        # NER 예측
        ner_logits = outputs['ner_logits'].detach()
        ner_preds = torch.argmax(ner_logits, dim=-1)
        
        # Intent 예측
        intent_logits = outputs['intent_logits'].detach()
        intent_preds = torch.argmax(intent_logits, dim=-1)
        
        # 레이블
        ner_labels = inputs['ner_labels'].detach() if 'ner_labels' in inputs else None
        intent_labels = inputs['intent_labels'].detach() if 'intent_labels' in inputs else None
        
        return (
            loss.detach(),
            (ner_preds, intent_preds),
            (ner_labels, intent_labels)
        )

def compute_metrics(eval_pred):
    """
    평가 메트릭 계산
    """
    predictions, labels = eval_pred
    ner_preds, intent_preds = predictions
    ner_labels, intent_labels = labels
    
    # Intent Accuracy
    intent_acc = accuracy_score(intent_labels, intent_preds)
    intent_f1 = f1_score(intent_labels, intent_preds, average='weighted')
    
    # NER Accuracy (패딩 제외)
    ner_preds_flat = []
    ner_labels_flat = []
    for pred_seq, label_seq in zip(ner_preds, ner_labels):
        for pred, label in zip(pred_seq, label_seq):
            if label != -100:
                ner_preds_flat.append(pred)
                ner_labels_flat.append(label)
    
    ner_acc = accuracy_score(ner_labels_flat, ner_preds_flat)
    
    return {
        'intent_accuracy': intent_acc,
        'intent_f1': intent_f1,
        'ner_accuracy': ner_acc,
    }

# ============================================
# 9. Training Arguments
# ============================================
print("\n⚙️  학습 파라미터 설정...")
training_args = TrainingArguments(
    output_dir="./results_voice_command",
    num_train_epochs=5,
    per_device_train_batch_size=16,
    per_device_eval_batch_size=16,
    eval_strategy="epoch",
    save_strategy="epoch",
    learning_rate=3e-5,
    weight_decay=0.01,
    warmup_steps=500,
    logging_dir="./logs",
    logging_steps=100,
    load_best_model_at_end=True,
    metric_for_best_model="intent_accuracy",
    save_total_limit=2,
    report_to="none",
    push_to_hub=False,
)

# ============================================
# 10. Trainer 생성 및 학습
# ============================================
print("\n🚀 Trainer 생성...")
trainer = MultiTaskTrainer(
    model=model,
    args=training_args,
    train_dataset=train_dataset,
    eval_dataset=eval_dataset,
    compute_metrics=compute_metrics,
    callbacks=[EarlyStoppingCallback(early_stopping_patience=3)]
)

print("\n" + "="*60)
print("🎓 학습 시작!")
print("="*60)

trainer.train()

print("\n✅ 학습 완료!")

# ============================================
# 11. 모델 저장
# ============================================
save_path = "./saved_voice_command_model"
os.makedirs(save_path, exist_ok=True)

# 모델 저장
torch.save(model.state_dict(), f"{save_path}/pytorch_model.bin")
tokenizer.save_pretrained(save_path)

# 설정 저장
config = {
    'model_name': model_name,
    'ner_tags': NER_TAGS,
    'ner_tag2id': ner_tag2id,
    'ner_id2tag': ner_id2tag,
    'intents': INTENTS,
    'intent2id': intent2id,
    'id2intent': id2intent,
}

with open(f"{save_path}/config.json", 'w', encoding='utf-8') as f:
    json.dump(config, f, ensure_ascii=False, indent=2)

print(f"\n💾 모델 저장 완료: {save_path}")

# ============================================
# 12. 최종 평가
# ============================================
print("\n📊 최종 평가...")
eval_results = trainer.evaluate()
print("\n평가 결과:")
for key, value in eval_results.items():
    print(f"   {key}: {value:.4f}")

print("\n" + "="*60)
print("✅ 모든 작업 완료!")
print("="*60)