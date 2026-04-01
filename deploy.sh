#!/bin/bash
echo "=== Building Angular frontend ==="
npm install
npx ng build --configuration=production
echo ""
echo "=== Frontend output in dist/patitoland-app/browser/ ==="
echo "Upload the contents of that folder to your Piensa hosting via FTP"
echo ""
echo "=== Building Spring Boot backend ==="
cd backend
mvn clean package -DskipTests
echo ""
echo "=== Backend JAR at backend/target/patitoland-backend-1.0.0.jar ==="
echo "Push to Railway: cd backend && railway up"
