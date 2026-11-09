| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 80.7 s ± 2.2 | 1.0x | 2.08 GB | 0% | 2.08 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 16.4 s ± 0.4 | 4.9x | 2.56 GB | +23% | 1.94 GB | 1016 / 800 | +800 / -0 |
| `tsrs` | 10.8 s ± 0.1 | 7.5x | 1.28 GB | -39% | 0.67 GB | 1016 / 800 | +800 / -0 |
| `tsrs-fast` | 10.3 s ± 0.4 | 7.8x | 1.17 GB | -44% | 0.59 GB | 1016 / 800 | +800 / -0 |
| `tsgo-flat` | 11.4 s ± 0.3 | 7.1x | 2.39 GB | +15% | 1.88 GB | 809 / 800 | +800 / -0 |
| `tsrs-flat-4g` | 7.1 s ± 0.1 | 11.4x | 1.16 GB | -45% | 0.66 GB | 809 / 800 | +800 / -0 |
| `tsrs-flat-384m` | 7.1 s ± 0.1 | 11.3x | 1.15 GB | -45% | 0.65 GB | 809 / 800 | +800 / -0 |
| `tsrs-flat-go` | 7.3 s ± 0.2 | 11.1x | 1.28 GB | -38% | 0.78 GB | 809 / 800 | +800 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
