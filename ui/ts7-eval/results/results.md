| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 88.6 s ± 1.8 | 1.0x | 2.56 GB | 0% | 2.56 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 20.3 s ± 0.5 | 4.4x | 3.15 GB | +23% | 2.53 GB | 497 / 396 | +396 / -0 |
| `tsrs` | 11.5 s ± 0.4 | 7.7x | 1.35 GB | -47% | 0.73 GB | 497 / 396 | +396 / -0 |
| `tsrs-fast` | 11.5 s ± 0.3 | 7.7x | 1.24 GB | -52% | 0.63 GB | 497 / 396 | +396 / -0 |
| `tsgo-flat` | 13.2 s ± 0.7 | 6.7x | 2.58 GB | +1% | 2.05 GB | 401 / 394 | +394 / -0 |
| `tsrs-flat-4g` | 7.8 s ± 0.2 | 11.4x | 1.22 GB | -53% | 0.71 GB | 401 / 394 | +394 / -0 |
| `tsrs-flat-384m` | 8.1 s ± 0.1 | 11.0x | 1.21 GB | -53% | 0.70 GB | 401 / 394 | +394 / -0 |
| `tsrs-flat-go` | 7.9 s ± 0.2 | 11.2x | 1.36 GB | -47% | 0.84 GB | 401 / 394 | +394 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
