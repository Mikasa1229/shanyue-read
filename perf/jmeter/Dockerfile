FROM eclipse-temurin:17-jre-jammy

ARG JMETER_VERSION=5.6.3
ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV PATH=${JMETER_HOME}/bin:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && curl -fsSL "https://downloads.apache.org/jmeter/binaries/apache-jmeter-${JMETER_VERSION}.zip" -o /tmp/jmeter.zip \
    && unzip -q /tmp/jmeter.zip -d /opt \
    && rm -f /tmp/jmeter.zip \
    && apt-get purge -y --auto-remove curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tests
ENTRYPOINT ["jmeter"]
