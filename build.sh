#!/bin/bash

# Load environment variables from the .env file
if [ -f default.env ]; then
  export $(cat default.env | sed 's/#.*//g' | xargs)
fi

# Run the Maven command
mvn clean install
