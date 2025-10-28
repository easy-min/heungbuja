import torch
import torch.nn as nn
from transformers import AutoModel

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
        pooled_output = self.dropout(outputs.pooler_output)         # [batch, 768]
        
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
