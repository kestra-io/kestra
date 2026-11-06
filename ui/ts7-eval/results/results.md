| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 80.8 s ± 1.2 | 1.0x | 2.09 GB | 0% | 2.08 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 16.9 s ± 0.5 | 4.8x | 2.39 GB | +15% | 1.78 GB | 1337 / 1095 | +1095 / -0 |
| `tsrs` | 10.7 s ± 0.1 | 7.6x | 1.26 GB | -40% | 0.68 GB | 1337 / 1095 | +1095 / -0 |
| `tsrs-fast` | 10.5 s ± 0.1 | 7.7x | 1.16 GB | -44% | 0.59 GB | 1337 / 1095 | +1095 / -0 |
| `tsgo-flat` | 11.6 s ± 0.2 | 6.9x | 2.34 GB | +12% | 1.83 GB | 961 / 952 | +952 / -0 |
| `tsrs-flat-4g` | 7.1 s ± 0.1 | 11.3x | 1.15 GB | -45% | 0.66 GB | 961 / 952 | +952 / -0 |
| `tsrs-flat-384m` | 7.2 s ± 0.1 | 11.2x | 1.15 GB | -45% | 0.65 GB | 961 / 952 | +952 / -0 |
| `tsrs-flat-go` | 7.2 s ± 0.1 | 11.2x | 1.28 GB | -39% | 0.77 GB | 961 / 952 | +952 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
