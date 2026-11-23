#!/bin/bash
set -e

echo "=== Starting Online Examination Management System on Render ==="

# 1. Optimize MariaDB for minimal RAM footprint (suitable for Render 512MB free tier)
mkdir -p /etc/mysql/mariadb.conf.d/
cat << 'EOF' > /etc/mysql/mariadb.conf.d/99-render-low-ram.cnf
[mysqld]
performance_schema = OFF
innodb_buffer_pool_size = 32M
key_buffer_size = 16M
max_connections = 25
table_open_cache = 64
innodb_log_buffer_size = 4M
EOF

# 2. Start MariaDB server
echo "Starting database service..."
service mariadb start || /etc/init.d/mariadb start

# Wait for MariaDB to accept connections
for i in {1..30}; do
    if mariadb-admin ping --silent 2>/dev/null; then
        echo "Database is ready."
        break
    fi
    sleep 1
done

# 3. Initialize database, user, and sample data
echo "Configuring database schema..."
mariadb -u root -e "CREATE DATABASE IF NOT EXISTS online_exam_db;"
mariadb -u root -e "CREATE USER IF NOT EXISTS 'exam_user'@'localhost' IDENTIFIED BY 'exam123';"
mariadb -u root -e "GRANT ALL PRIVILEGES ON online_exam_db.* TO 'exam_user'@'localhost'; FLUSH PRIVILEGES;"
mariadb -u root online_exam_db < /app/sql/database_setup.sql 2>/dev/null || true
mariadb -u root online_exam_db < /app/sql/sample_data.sql 2>/dev/null || true

# 4. Start X Virtual Framebuffer (Xvfb)
echo "Starting Virtual Display (Xvfb)..."
export DISPLAY=:0
Xvfb :0 -screen 0 1280x800x24 -ac +extension GLX +render -noreset &
sleep 1

# 5. Start Openbox window manager (gives clean window decorations & proper focus)
echo "Starting Window Manager (Openbox)..."
openbox &

# 6. Start x11vnc server
echo "Starting VNC bridge..."
x11vnc -display :0 -nopw -listen 127.0.0.1 -xkb -forever -shared -bg

# 7. Start noVNC / websockify on Render PORT (defaults to 10000)
WEB_PORT="${PORT:-10000}"
echo "Starting noVNC Web Gateway on port ${WEB_PORT}..."
websockify --web=/usr/share/novnc/ 0.0.0.0:${WEB_PORT} 127.0.0.1:5900 &

# 8. Set Java display and environment variables
export DB_URL="jdbc:mysql://localhost:3306/online_exam_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
export DB_USER="exam_user"
export DB_PASS="exam123"

# 9. Launch the Java Application with auto-restart supervisor loop
echo "=== System Ready! Web Interface live on port ${WEB_PORT} ==="
cd /app
while true; do
    echo "Launching Online Examination System..."
    java -Xmx256m \
         -Dawt.useSystemAAFontSettings=on \
         -Dswing.aatext=true \
         -Dsun.java2d.opengl=false \
         -cp "OnlineExamSystem.jar:lib/*" \
         com.onlineexam.main.Main || true
    echo "Application window closed. Relaunching in 2 seconds..."
    sleep 2
done
