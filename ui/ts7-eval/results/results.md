| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 106.3 s ± 3.3 | 1.0x | 2.12 GB | 0% | 2.12 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 21.7 s ± 0.4 | 4.9x | 3.08 GB | +45% | 2.48 GB | 503 / 402 | +402 / -0 |
| `tsrs` | 13.8 s ± 0.8 | 7.7x | 1.34 GB | -37% | 0.72 GB | 503 / 402 | +402 / -0 |
| `tsrs-fast` | 13.4 s ± 0.3 | 7.9x | 1.24 GB | -42% | 0.62 GB | 503 / 402 | +402 / -0 |
| `tsgo-flat` | 14.8 s ± 0.8 | 7.2x | 2.55 GB | +20% | 2.02 GB | 407 / 400 | +400 / -0 |
| `tsrs-flat-4g` | 8.7 s ± 0.2 | 12.3x | 1.23 GB | -42% | 0.71 GB | 407 / 400 | +400 / -0 |
| `tsrs-flat-384m` | 8.8 s ± 0.5 | 12.1x | 1.21 GB | -43% | 0.70 GB | 407 / 400 | +400 / -0 |
| `tsrs-flat-go` | 9.2 s ± 0.4 | 11.5x | 1.33 GB | -37% | 0.82 GB | 407 / 400 | +400 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
