import torch

checkpoint_path = "./app/brandnewTrain/checkpoints/brandnew_model_v2.pt"
checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=False)

print("체크포인트 키 목록:")
for key in checkpoint.keys():
    print(f"  - {key}")

print(f"\n체크포인트 타입: {type(checkpoint)}")
