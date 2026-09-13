# Postgres for the deployment — the same pgvector image docker-compose runs locally, with the
# script that creates authdb and vectordb baked in.
#
# Railway has no bind mounts, so the local trick of mounting ./init into the container is not
# available there. Copying the script into the image does the same job: the entrypoint runs it once,
# on an empty data directory, and never again.
FROM pgvector/pgvector:pg16

COPY init/ /docker-entrypoint-initdb.d/

# A Railway volume is mounted at /var/lib/postgresql/data and arrives with a lost+found directory
# in it. initdb refuses a non-empty directory, so the cluster lives one level down. Only this image
# sets it: docker-compose uses the plain image and keeps its existing data where it already is.
ENV PGDATA=/var/lib/postgresql/data/pgdata
