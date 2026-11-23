# Multi-stage / lightweight Debian base for Render cloud deployment
FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK 17, Xvfb virtual display, Openbox, x11vnc, noVNC web bridge, MariaDB, and fonts
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre \
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

# Configure noVNC index.html to automatically connect and scale to user browser
RUN ln -sf /usr/share/novnc/vnc.html /usr/share/novnc/index.html && \
    sed -i 's/UI.initSetting("autoconnect", false);/UI.initSetting("autoconnect", true);/g' /usr/share/novnc/app/ui.js 2>/dev/null || true && \
    sed -i 's/UI.initSetting("resize", "off");/UI.initSetting("resize", "scale");/g' /usr/share/novnc/app/ui.js 2>/dev/null || true

WORKDIR /app

# Copy application files
COPY OnlineExamSystem.jar /app/
COPY lib/ /app/lib/
COPY sql/ /app/sql/
COPY entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 10000

CMD ["/app/entrypoint.sh"]
