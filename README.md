# DrunkHeadphonesAdapterAndroidApp

## About
Mobile optional! companion app for [DrunkHeadphonesAdapter](https://github.com/drunkbatya/DrunkHeadphonesAdapter)

## Features
- Update adapter firmware via DFU
- Watch power and audio live data
- Edit settings

## Build
### Generate keystore
```bash
PASSWORD=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')
keytool -genkeypair \
    -keystore "dha.keystore" \
    -storetype PKCS12 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -alias "dha" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=DrunkHeadphonesAdapter"
```

### Build apks
```bash
git submodule update --init
docker build -t test \
    --secret id=keystore,type=file,src=./dha.keystore \
    --secret id=keystorepass,env=PASSWORD \
    --output type=local,dest=./out .
```
