Gradle AWS Elastic Container Registry Plugin [![CircleCI](https://circleci.com/bb/double16/gradle-aws-ecr-plugin.svg?style=svg&circle-token=6f261793ab1ee2dd674adb04bb334336eb65f54b)](https://circleci.com/bb/double16/gradle-aws-ecr-plugin)
=====================================================

Integrates Docker functions with AWS ECR (Elastic Container Registry) using `gradle-docker-plugin`.

Features
--------

This contains following features:

  * Handles authentication with AWS ECR
  * Does not interfere with use of other registries

Getting Started
---------------

The plugin will detect the AWS ECR url in docker tasks. Only one AWS account is supported at this time. The AWS credentials can be supplied in the following ways based on the AWS SDK:

* Environment Variables - AWS_PROFILE, AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY (RECOMMENDED since they are recognized by all the AWS SDKs and CLI except for .NET), or AWS_ACCESS_KEY and AWS_SECRET_KEY (only recognized by Java SDK)
* Java System Properties - aws.profile, aws.accessKeyId and aws.secretKey
* Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI
* Credentials delivered through the Amazon EC2 container service if AWS_CONTAINER_CREDENTIALS_RELATIVE_URI" environment variable is set and security manager has permission to access the variable, Instance profile credentials delivered through the Amazon EC2 metadata service

Additionally, the following project properties can be specified that take precedence over the AWS SDK:
* awsAccessKeyId
* awsSecretAccessKey
```shell
$ ./gradlew -PawsAccessKeyId=MY_ACCESS_ID -PawsSecretAccessKey=MY_SECRET
```

The secret access key (token) must be permitted to create ECR tokens and read/write (as needed) to the repositories. An example inline security policy that allows a group all access to ECR follows. You may want to look into restricting this, to prevent deleting a repo, for example.
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "Stmt1461121381000",
            "Effect": "Allow",
            "Action": [
                "ecr:*"
            ],
            "Resource": [
                "*"
            ]
        }
    ]
}
```

You will need both the `gradle-docker-plugin` and `gradle-aws-ecr-plugin`.

```groovy
plugins {
  id "com.bmuschko.docker-remote-api" version "5.4.0"
  id "com.patdouble.awsecr" version "0.7.0"
}
```

Then set the Docker registry to your ECR URL. The URL is given by the AWS ECR console. Note that this clause is defined by the `gradle-docker-plugin`. Neither username nor password should be set, the `gradle-aws-ecr-plugin` will handle that.

```groovy
docker {
    registryCredentials {
        url = 'https://123456789012.dkr.ecr.us-east-1.amazonaws.com'
    }
}
```

All Docker tasks such as `DockerPullImage`, `DockerPushImage`, etc. that are configured with the ECR registry URL will get a temporary ECR token. No further configuration is necessary. It is possible to set the registry URL for individual tasks. For those tasks with a registry that is not ECR, the username and password will not be set with an ECR token.

```groovy
task buildImage(type: DockerBuildImage) {
    dependsOn createDockerfile
    inputDir = createDockerfile.destFile.parentFile
    tags.add('123456789012.dkr.ecr.us-east-1.amazonaws.com/myimage:latest')
}
```

```groovy
docker {
    javaApplication {
        baseImage = 'openjdk:8u181-jdk'
        ports = [9090, 5701]
        tags = '123456789012.dkr.ecr.us-east-1.amazonaws.com/jettyapp:1.115'
    }
}
```

Contributions
-------------

This is open source software licensed under the Apache License Version 2.0.
Issues and pull requests are welcome.

Build and run unit tests:
```shell
$ ./gradlew check
```

- Test results in `build/reports/tests/test/index.html`
- Test Coverage in `build/reports/clover/index.html`
- CodeNarc results in `build/reports/codenarc/main.html`

Acceptance test to ensure the plugin will be applied:
```shell
$ ./gradlew -p acceptance-test test
```

Change Log
----------

## 0.7.0
- AWS SDK 1.12, Thanks Mike Anderson!

## 0.6.2
- Fix for Gradle 7.0. Thanks Pavel Grigorenko!

## 0.6.1
- PopulateECRCredentials.groovy doesn't set registry credentials using property access. Thanks Terence Schumacher! [PR #15](https://bitbucket.org/double16/gradle-aws-ecr-plugin/pull-requests/15)

## 0.6.0
- Compatibility with Gradle 6 and gradle-docker-plugin 6.0.0. Thanks Joris Kuipers! [Issue #19](https://bitbucket.org/double16/gradle-aws-ecr-plugin/issues/19/compatibility-with-gradle-docker-plugin)

## 0.5.2
- switch AWS_PROFILE property to the proper "aws.profile" property already considered by aws provider chain. Bad Mason!

## 0.5.1
- Consider value of `AWS_PROFILE` system property if environment variable isn't set. Thanks Mason!

## 0.5.0
- Build with Gradle 5.1.1
- Requires Gradle plugin `com.bmuschko.docker-remote-api` >= 4.0

## 0.4.0
- Disable `PopulateECRCredentials` task if no ECR registry is found so the build can continue.
- Consider value of `AWS_PROFILE` environment variable when caching credentials. [Issue #9](https://bitbucket.org/double16/gradle-aws-ecr-plugin/issues/9/docker-daemon-seems-to-interfere-with)

## 0.3.3
- Fix issue with mixing ECR repository and non-ECR repositories in the same build. [Issue #8](https://bitbucket.org/double16/gradle-aws-ecr-plugin/issues/8/errors-executing-populateecrcredentials)

## 0.3.2
- Fix CircleCI 2.0 for building tags

## 0.3.1
- Documentation and code quality (tests, coverage, etc.) improvements
- Build with Gradle 3.5

## 0.3
Thanks @jonathan_naguin !
- Use AWS credentials Provider chain #5 (@jonathan_naguin)

## 0.2
Thanks @mwhipple !
- Move fetching of credentials to execution phase (@mwhipple)
- Relax ECR URL matching to not require `https` scheme, fixes #3 (@mwhipple)
- Accept credentials as properties on the task, fixes #2 (@mwhipple)

## 0.1.4
- Initial release
