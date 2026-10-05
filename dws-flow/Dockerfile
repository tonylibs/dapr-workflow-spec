# syntax=docker/dockerfile:1

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# version.txt feeds <Version> in the csproj, so restore needs it too.
COPY dws-flow.csproj version.txt ./
RUN dotnet restore

COPY . ./
# Name the project: dws-flow.slnx sits beside it, and a bare publish would build the
# (never restored) test project through the solution.
RUN dotnet publish dws-flow.csproj --configuration Release --no-restore --output /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

COPY --from=build /app/publish ./
USER $APP_UID

EXPOSE 8080
ENV ASPNETCORE_URLS=http://+:8080

ENTRYPOINT ["dotnet", "dws-flow.dll"]
