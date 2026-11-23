# Single-stage Debian Bookworm image (fully supported and resolved by Docker Hub & Render)
FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK 17 JDK, Xvfb virtual display, Openbox, x11vnc, noVNC web bridge, MariaDB, and fonts
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    xvfb \
    x11vnc \
    openbox \
    novnc \
    websockify \
    mariadb-server \
    mariadb-client \
    fonts-dejavu-core \
    fonts-freefont-ttf \
    net-tools \
    procps \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set up automatic connect and resize redirect for noVNC
RUN echo '<!DOCTYPE html><html><head><meta http-equiv="refresh" content="0; url=vnc.html?autoconnect=true&resize=scale&reconnect=true"></head><body><p>Connecting to Online Examination System...</p></body></html>' > /usr/share/novnc/index.html

WORKDIR /app

# Copy application files and source code
COPY OnlineExamSystem.jar /app/OnlineExamSystem.jar
COPY ["java files", "/app/java files/"]
COPY lib/ /app/lib/
COPY sql/ /app/sql/
COPY entrypoint.sh /app/entrypoint.sh

# Ensure the JAR is compiled natively with Java 17 bytecode (version 61.0) inside Debian
RUN mkdir -p /app/out && \
    find "/app/java files" -name "*.java" > /app/sources.txt && \
    javac -cp "/app/lib/*" -d /app/out @/app/sources.txt && \
    jar cfe /app/OnlineExamSystem.jar com.onlineexam.main.Main -C /app/out . && \
    rm -rf /app/sources.txt /app/out "/app/java files"

RUN chmod +x /app/entrypoint.sh

EXPOSE 10000

CMD ["/app/entrypoint.sh"]
