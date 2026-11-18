# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

# --- run ---
# OCR runs in the browser (Tesseract.js), so the image needs no Tesseract install;
# without tessdata the server skips its own OCR and only calls Gemini.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/split-bill-*.jar app.jar
# Render's free plan has 512 MB
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
