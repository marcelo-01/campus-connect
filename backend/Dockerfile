# Imagem da API. Compila dentro do Docker e pula os testes: eles precisam do Docker para o
# Testcontainers e rodam na máquina de desenvolvimento, antes de publicar.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# O cache do ~/.m2 fica entre um build e outro, então só a primeira vez baixa todas as dependências.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /dados/uploads && chown app:app /dados/uploads
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
# 60% da memória do contêiner vai para o heap. O resto fica para metaspace, threads e buffers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
