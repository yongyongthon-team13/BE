# OCI deployment

The API and MySQL run on the OCI Always Free VM in Tokyo. The API container is built from this repository and MySQL data stays in the named `mysql-data` volume.

## Server setup

On the VM, keep `.env.oci` beside `compose.oci.yaml`. Create it from `.env.oci.example` and set the database credentials, `APP_DATA_KEY`, `APP_JWT_SECRET`, and any provider keys. Do not commit `.env.oci` or copy it from a workstation during deployment.

## GitHub Actions setup

Add these repository Actions secrets in `yongyongthon-team13/BE`:

- `OCI_HOST`: the VM's public IP address.
- `OCI_USER`: `opc`.
- `OCI_SSH_KEY`: a dedicated private SSH key whose public key is installed in the VM user's `~/.ssh/authorized_keys`.
- `OCI_KNOWN_HOSTS`: the pinned SSH host-key entry for the VM.

The `Deploy backend to OCI` workflow runs when a commit reaches `main`. It copies repository files without `.env*`, `.git`, Gradle caches, or build output, then runs `docker compose ... up -d --build api` on the VM. It records the deployed commit in `.deployed-commit`. MySQL's named volume is not removed by this command.

The SSH key lets the workflow run Docker Compose as `opc`, which can control the VM. Protect `main` with pull-request review and restrict who can merge workflow changes. The SSH ingress rule must allow the GitHub-hosted runner to connect; runner addresses change, so avoid allowing broad SSH access for production without an explicit network design.

## Manual operations

From `/home/opc/gilbut-backend` on the VM:

```sh
docker compose --env-file .env.oci -f compose.oci.yaml ps
docker compose --env-file .env.oci -f compose.oci.yaml logs --tail=100 api
```
