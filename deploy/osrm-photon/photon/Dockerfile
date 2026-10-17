# Photon (https://github.com/komoot/photon, Apache-2.0) as a plain Java service.
FROM eclipse-temurin:21-jre
ARG PHOTON_VERSION=0.7.0
WORKDIR /photon
RUN apt-get update -q && apt-get install -y -q --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o photon.jar "https://github.com/komoot/photon/releases/download/${PHOTON_VERSION}/photon-${PHOTON_VERSION}.jar"
EXPOSE 2322
# The India index is unpacked into /photon/data/photon_data by prepare.sh.
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar photon.jar -data-dir /photon/data -listen-ip 0.0.0.0 -listen-port 2322 -cors-any"]
