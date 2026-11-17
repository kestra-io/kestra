| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 89.5 s ± 1.9 | 1.0x | 2.12 GB | 0% | 2.12 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 18.6 s ± 0.6 | 4.8x | 3.12 GB | +47% | 2.50 GB | 615 / 494 | +494 / -0 |
| `tsrs` | 12.2 s ± 0.8 | 7.3x | 1.33 GB | -38% | 0.72 GB | 615 / 494 | +494 / -0 |
| `tsrs-fast` | 11.8 s ± 0.5 | 7.6x | 1.21 GB | -43% | 0.61 GB | 615 / 494 | +494 / -0 |
| `tsgo-flat` | 12.4 s ± 0.3 | 7.2x | 2.44 GB | +15% | 1.90 GB | 501 / 494 | +494 / -0 |
| `tsrs-flat-4g` | 7.8 s ± 0.1 | 11.4x | 1.19 GB | -44% | 0.68 GB | 501 / 494 | +494 / -0 |
| `tsrs-flat-384m` | 7.9 s ± 0.1 | 11.3x | 1.19 GB | -44% | 0.68 GB | 501 / 494 | +494 / -0 |
| `tsrs-flat-go` | 8.6 s ± 0.5 | 10.4x | 1.31 GB | -38% | 0.80 GB | 501 / 494 | +494 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
