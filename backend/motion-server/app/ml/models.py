"""Pose GCN + Temporal CNN 모델 정의."""

from __future__ import annotations

from typing import Iterable, Sequence

import torch
import torch.nn as nn

from .constants import BODY_LANDMARK_INDICES

ORIGINAL_BODY_EDGES = (
    (11, 12),
    (11, 13),
    (13, 15),
    (15, 17),
    (17, 19),
    (19, 21),
    (12, 14),
    (14, 16),
    (16, 18),
    (18, 20),
    (20, 22),
    (11, 23),
    (12, 24),
    (23, 24),
    (23, 25),
    (25, 27),
    (27, 29),
    (29, 31),
    (24, 26),
    (26, 28),
    (28, 30),
    (30, 32),
    (23, 27),
    (24, 28),
)

NUM_POSE_LANDMARKS = len(BODY_LANDMARK_INDICES)
INDEX_MAP = {original: idx for idx, original in enumerate(BODY_LANDMARK_INDICES)}
POSE_EDGES = tuple(
    (INDEX_MAP[i], INDEX_MAP[j])
    for i, j in ORIGINAL_BODY_EDGES
    if i in INDEX_MAP and j in INDEX_MAP
)


def build_normalized_adjacency(num_nodes: int, edges: Iterable[tuple[int, int]]) -> torch.Tensor:
    adjacency = torch.eye(num_nodes, dtype=torch.float32)
    for i, j in edges:
        adjacency[i, j] = 1.0
        adjacency[j, i] = 1.0

    degree = adjacency.sum(dim=1)
    inv_sqrt_degree = torch.where(degree > 0, degree.pow(-0.5), torch.zeros_like(degree))
    d_mat = torch.diag(inv_sqrt_degree)
    normalized = d_mat @ adjacency @ d_mat
    return normalized


class GraphConvLayer(nn.Module):
    def __init__(
        self,
        in_channels: int,
        out_channels: int,
        adjacency: torch.Tensor,
        activation: nn.Module | None = nn.ReLU(),
        bias: bool = True,
    ) -> None:
        super().__init__()
        self.register_buffer("adjacency", adjacency)
        self.linear = nn.Linear(in_channels, out_channels, bias=bias)
        self.activation = activation

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        agg = torch.einsum("vw,bwc->bvc", self.adjacency, x)
        out = self.linear(agg)
        if self.activation is not None:
            out = self.activation(out)
        return out


class SpatialGCN(nn.Module):
    def __init__(
        self,
        in_channels: int,
        hidden_dims: Sequence[int],
        adjacency: torch.Tensor,
        dropout: float = 0.0,
    ) -> None:
        super().__init__()
        dims = [in_channels, *hidden_dims]
        layers: list[nn.Module] = []
        for idx in range(len(dims) - 1):
            activation = nn.ReLU() if idx < len(dims) - 2 else None
            layers.append(
                GraphConvLayer(
                    in_channels=dims[idx],
                    out_channels=dims[idx + 1],
                    adjacency=adjacency,
                    activation=activation,
                )
            )
            if dropout > 0 and idx < len(dims) - 2:
                layers.append(nn.Dropout(dropout))
        self.layers = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        batch_size, seq_len, channels, vertices = x.shape
        x = x.permute(0, 1, 3, 2).contiguous().view(batch_size * seq_len, vertices, channels)
        x = self.layers(x)
        out_channels = x.shape[-1]
        x = x.view(batch_size, seq_len, vertices, out_channels)
        return x


class TemporalConvBlock(nn.Module):
    def __init__(
        self,
        in_channels: int,
        out_channels: int,
        kernel_size: int = 3,
        dropout: float = 0.0,
    ) -> None:
        super().__init__()
        padding = kernel_size // 2
        self.net = nn.Sequential(
            nn.Conv1d(in_channels, out_channels, kernel_size=kernel_size, padding=padding),
            nn.BatchNorm1d(out_channels),
            nn.ReLU(),
            nn.Dropout(dropout) if dropout > 0 else nn.Identity(),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


class PoseGCNTemporalModel(nn.Module):
    def __init__(
        self,
        num_classes: int,
        in_channels: int = 2,
        gcn_hidden_dims: Sequence[int] = (64, 128),
        temporal_channels: Sequence[int] = (128, 256),
        dropout: float = 0.3,
    ) -> None:
        super().__init__()
        adjacency = build_normalized_adjacency(NUM_POSE_LANDMARKS, POSE_EDGES)
        self.register_buffer("adjacency", adjacency)

        self.spatial_gcn = SpatialGCN(
            in_channels=in_channels,
            hidden_dims=gcn_hidden_dims,
            adjacency=adjacency,
            dropout=dropout,
        )

        temporal_layers: list[nn.Module] = []
        temporal_dims = [gcn_hidden_dims[-1], *temporal_channels]
        for idx in range(len(temporal_dims) - 1):
            temporal_layers.append(
                TemporalConvBlock(
                    in_channels=temporal_dims[idx],
                    out_channels=temporal_dims[idx + 1],
                    kernel_size=3,
                    dropout=dropout,
                )
            )

        self.temporal_net = nn.Sequential(*temporal_layers)
        self.global_pool = nn.AdaptiveAvgPool1d(1)
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.Linear(temporal_dims[-1], 256),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(256, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        spatial = self.spatial_gcn(x)
        spatial = spatial.permute(0, 3, 1, 2)
        spatial = spatial.mean(dim=3)
        temporal = self.temporal_net(spatial)
        pooled = self.global_pool(temporal)
        logits = self.classifier(pooled)
        return logits


