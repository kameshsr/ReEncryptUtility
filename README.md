# Re Encrypt Utility

## Overview
This utility is used to re-encrypt data in database and object store with new key.

## Functionality
- Reads the database and object store and re-encrypts the data with new key.


## Setup steps:

### Linux (Docker) 

1. Download the latest version of [re-encrypt-utility](https://github.com/kameshsr/re-encrypt-utility.git)

```
git clone https://github.com/kameshsr/re-encrypt-utility.git
```


2. Go to root directory of the project and run below command.
```
mvn clean install
```

3. Build docker image using below command.
```
docker build -t re-encrypt-utility .
```

4. Run docker image using below command.
```
docker run -p 8081:8081 -it --net=host re-encrypt-docker
```