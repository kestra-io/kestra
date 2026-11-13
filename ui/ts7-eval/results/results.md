| Run | Wall time, mean ± σ | Speedup vs vue-tsc | Peak RSS, process tree | Δ RSS vs vue-tsc | Largest process | Diagnostics (raw / folded) | Δ vs vue-tsc |
|---|---|---|---|---|---|---|---|
| `vue-tsc` | 72.7 s ± 4.1 | 1.0x | 2.09 GB | 0% | 2.09 GB | 0 / 0 | +0 / -0 |
| `tsgo` | 14.4 s ± 0.4 | 5.1x | 2.61 GB | +25% | 1.98 GB | 929 / 730 | +730 / -0 |
| `tsrs` | 9.3 s ± 0.2 | 7.8x | 1.28 GB | -39% | 0.69 GB | 929 / 730 | +730 / -0 |
| `tsrs-fast` | 9.0 s ± 0.1 | 8.1x | 1.18 GB | -44% | 0.59 GB | 929 / 730 | +730 / -0 |
| `tsgo-flat` | 10.1 s ± 0.2 | 7.2x | 2.37 GB | +13% | 1.84 GB | 738 / 730 | +730 / -0 |
| `tsrs-flat-4g` | 6.1 s ± 0.1 | 11.9x | 1.17 GB | -44% | 0.66 GB | 738 / 730 | +730 / -0 |
| `tsrs-flat-384m` | 5.7 s ± 0.3 | 12.7x | 1.16 GB | -44% | 0.66 GB | 738 / 730 | +730 / -0 |
| `tsrs-flat-go` | 5.6 s ± 0.1 | 13.0x | 1.29 GB | -38% | 0.78 GB | 738 / 730 | +730 / -0 |

- `tsrs` vs `tsgo`: identical diagnostics
- `tsrs-fast` vs `tsgo`: identical diagnostics
- `tsrs-flat-4g` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-384m` vs `tsgo-flat`: identical diagnostics
- `tsrs-flat-go` vs `tsgo-flat`: identical diagnostics
