# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre
# Tess4J calls the system Tesseract library through JNA; the -dev package adds the libtesseract.so link it looks for
RUN apt-get update \
 && apt-get install -y --no-install-recommends libtesseract-dev curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# "fast" models: the free instance has a tiny CPU share and the "best" models take minutes per photo
RUN mkdir tessdata \
 && curl -fsSL -o tessdata/tha.traineddata https://github.com/tesseract-ocr/tessdata_fast/raw/main/tha.traineddata \
 && curl -fsSL -o tessdata/eng.traineddata https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata
COPY --from=build /src/target/split-bill-*.jar app.jar
# Render's free plan has 512 MB; leave room for Tesseract's native memory
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC" TESSDATA=/app/tessdata
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
