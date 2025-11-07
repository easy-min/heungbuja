This directory holds Let's Encrypt data when running certbot via Docker.

Structure (created at runtime):
- conf/ -> mapped to /etc/letsencrypt
- www/  -> mapped to /var/www/certbot (HTTP-01 webroot)

These paths are ignored by git via root .gitignore.


