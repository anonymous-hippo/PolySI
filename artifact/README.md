# Reproducing results

1. Build PolySI, Cobra and dbcop

   Please follow their instructions in respective READMEs

   Cobra and dbcop are optional. If you don't build them, only PolySI will be used to run the benchmarks.

2. Modify the paths in `repro/reproduce.sh` to point to the directories of data and verifiers

   The paths that needs to be modified are shown in the first few lines of the script.

3. Run `repro/reproduce.sh`

   Results are stored in `/tmp/csv`.

   Format of parameters (fig7): `${#sessions}_${#txns/session}_${#ops/txn}_${#keys}_${read_probability}_${key_distribution}`
