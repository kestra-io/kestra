| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 88.1 s ± 4.8 | 1.0x | 2.53 GB | 0% | 2.52 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 21.3 s ± 0.4 | 4.1x | 2.87 GB | +14% | 2.25 GB | 487 / 393 | +393 / -0 |
| `tsrs` | 12.4 s ± 0.3 | 7.1x | 1.34 GB | -47% | 0.73 GB | 487 / 393 | +393 / -0 |
| `tsrs-fast` | 12.2 s ± 0.6 | 7.2x | 1.25 GB | -51% | 0.63 GB | 487 / 393 | +393 / -0 |
| `tsgo-flat` | 14.3 s ± 0.2 | 6.2x | 2.59 GB | +3% | 2.06 GB | 393 / 386 | +386 / -0 |
| `tsrs-flat-4g` | 8.9 s ± 0.3 | 9.9x | 1.22 GB | -52% | 0.71 GB | 393 / 386 | +386 / -0 |
| `tsrs-flat-384m` | 8.8 s ± 0.4 | 10.1x | 1.23 GB | -51% | 0.71 GB | 393 / 386 | +386 / -0 |
| `tsrs-flat-go` | 8.9 s ± 0.3 | 9.9x | 1.36 GB | -46% | 0.83 GB | 393 / 386 | +386 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
