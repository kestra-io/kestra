# Kestra Docker Image Documentation

Welcome to the official Docker image documentation for [Kestra](https://kestra.io). This document explains how to use Kestra's Docker images, along with the available tags and links to the installation and deployment guides.

## Getting Started

To pull the official Kestra Docker image, use the following command:

```bash
docker pull kestra/kestra:latest
```
Installation and Deployment Guide
For detailed instructions on installing and deploying Kestra, please visit our Installation Guide.
[Kestra Documentation](https://kestra.io/docs)
This guide includes:

- **Step-by-step instructions for setting up Kestra in a Docker environment.**
- **Configuration options for both local and cloud deployments.**
- **Details on using Kestra with Kubernetes, Helm, and Docker Compose.**

## Docker Image Tags

Kestra provides various Docker image tags to meet different needs. Below is an explanation of the key tags:

- **`latest`**: The `latest` tag points to the most recent stable release of Kestra. This is recommended for most users who want to keep up-to-date with stable features and security fixes.

    ```bash
    docker pull kestra/kestra:latest
    ```

- **`beta`**: The `beta` tag contains new features that are still in the testing phase. It's ideal for users who want to test upcoming features but may encounter bugs.

    ```bash
    docker pull kestra/kestra:beta
    ```

- **`vX.Y.Z`**: These tags correspond to specific versions of Kestra. Use them if you require a particular version for compatibility or stability reasons.

  Example: To pull version `0.3.0`, use:

    ```bash
    docker pull kestra/kestra:v0.3.0
    ```

You can find all available version tags on [Docker Hub](https://hub.docker.com/r/kestra/kestra/tags).

## Running Kestra

Once you have pulled the image, you can run Kestra using the following basic command:

```bash
docker run -d --name kestra -p 8080:8080 kestra/kestra:latest

