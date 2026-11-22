| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 85.2 s ± 6.0 | 1.0x | 2.57 GB | 0% | 2.57 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 19.1 s ± 0.5 | 4.5x | 2.89 GB | +12% | 2.23 GB | 488 / 394 | +394 / -0 |
| `tsrs` | 12.2 s ± 0.2 | 7.0x | 1.36 GB | -47% | 0.74 GB | 488 / 394 | +394 / -0 |
| `tsrs-fast` | 11.8 s ± 0.2 | 7.2x | 1.25 GB | -51% | 0.64 GB | 488 / 394 | +394 / -0 |
| `tsgo-flat` | 13.0 s ± 0.3 | 6.5x | 2.60 GB | +1% | 2.07 GB | 394 / 387 | +387 / -0 |
| `tsrs-flat-4g` | 8.0 s ± 0.2 | 10.7x | 1.23 GB | -52% | 0.71 GB | 394 / 387 | +387 / -0 |
| `tsrs-flat-384m` | 8.0 s ± 0.1 | 10.7x | 1.22 GB | -53% | 0.71 GB | 394 / 387 | +387 / -0 |
| `tsrs-flat-go` | 8.0 s ± 0.1 | 10.7x | 1.36 GB | -47% | 0.84 GB | 394 / 387 | +387 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
