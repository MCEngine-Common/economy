# Use an official Java 21 image as a base
FROM mcr.microsoft.com/devcontainers/java:21

# Install additional dependencies
RUN apt-get update && \
    apt-get install -y \
    curl \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /workspace

# Create a non-root user for security
RUN useradd -ms /bin/bash devcontainer
USER devcontainer

# Default command
CMD ["/bin/bash"]