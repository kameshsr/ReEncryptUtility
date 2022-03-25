# Re-Encrypt-Utility

## Overview
Utilities for data migration - for encryption key changes <br />
In 1.1.5, version of MOSIP the encryption key was changed to a new key. <br />
This utility will help you to migrate your data from the old key to the new key.

## Scenario 1 - Two different environments 

Environment 1 :  1.1.3 has a set of keys generated and data is encrypted with this set of keys. <br />
Environment 2 :  1.1.5 has a set of keys generated (different keys here, totally a new environment). <br />
For this scenario, the data will be got decrypted with 1.1.3 environment keys and then re-encrypts with 1.1.5 environment. <br />

## Scenario 2 - Same environment 

Environment : previously it was running 1.1.3 version of MOSIP. <br />
Generated a set of keys and then encrypted data with this set of keys. <br />
Now in the same environment 1.1.5 version of MOSIP has got upgraded but no new keys will get generated here. <br />
It's just application upgrade. <br />
For this scenario, the data will be decrypted with the upgraded version of MOSIP and re-encrypted with upgraded version of MOSIP. <br />

## Functionality 
Scenario 1- Reads the database and object store data from the old key and re-encrypts with the new key and store it in new database and object store.<br />
Scenario 2- Reads the database and object store data from the upgraded version of MOSIP and re-encrypts with the upgraded version of MOSIP and store it in same database and object store. <br />

## Setup steps:

### Linux (Docker) 

1. Download the latest version of [re-encrypt-utility](https://github.com/kameshsr/re-encrypt-utility.git)

```
git clone https://github.com/kameshsr/re-encrypt-utility.git
```

2. Two use Scenario 1 change isNewDatabase to true and Scenario 2 change isNewDatabase to false.
```
isNewDatabase=true
```

3. For Both Scenarios, change below properties in the [application.properties.](https://github.com/kameshsr/re-encrypt-utility/blob/master/src/main/resources/application.properties)

```
datasource.primary.jdbcUrl=jdbc:postgresql://{jdbc url}:{port}/{primary database name}
datasource.primary.username={username}
datasource.primary.password={password}
datasource.primary.driver-class-name=org.postgresql.Driver

mosip.base.url={Base URL for decryption}
cryptoResource.url=${mosip.base.url}/v1/keymanager

appId={appId}
clientId={clientId}
secretKey={secretKey}

decryptBaseUrl=${mosip.base.url}
encryptBaseUrl={encryptBaseUrl}

object.store.s3.url={object store url}
object.store.s3.accesskey={object store accesskey}
object.store.s3.secretkey={object store secretkey}
```

4. For Scenario 1 (Two Environments), change the below properties in the application.properties.
```
datasource.secondary.jdbcUrl=jdbc:postgresql://{jdbc url}:{port}/{secondary database name}
datasource.secondary.username={username}
datasource.secondary.password={password}
datasource.secondary.driver-class-name=org.postgresql.Driver

destinationObjectStore.s3.url={destination object store url}
destinationObjectStore.s3.access-key={destination object store accesskey}
destinationObjectStore.s3.secret-key={destination object store secretkey}
```

5. Go to root directory of the project and run below command.
```
mvn clean install
```

6. Build docker image using below command.
```
docker build -t re-encrypt-utility .
```

7. Run docker image using below command.
```
docker run -p 8081:8081 -it --net=host re-encrypt-docker
```