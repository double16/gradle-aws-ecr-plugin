Gradle AWS Elastic Container Registry Plugin Template [![CircleCI](https://circleci.com/bb/double16/gradle-aws-ecr-plugin.svg?style=svg&circle-token=6f261793ab1ee2dd674adb04bb334336eb65f54b)](https://circleci.com/bb/double16/gradle-aws-ecr-plugin)
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

* Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY (RECOMMENDED since they are recognized by all the AWS SDKs and CLI except for .NET), or AWS_ACCESS_KEY and AWS_SECRET_KEY (only recognized by Java SDK)
* Java System Properties - aws.accessKeyId and aws.secretKey
* Credential profiles file at the default location (~/.aws/credentials) shared by all AWS SDKs and the AWS CLI
* Credentials delivered through the Amazon EC2 container service if AWS_CONTAINER_CREDENTIALS_RELATIVE_URI" environment variable is set and security manager has permission to access the variable, Instance profile credentials delivered through the Amazon EC2 metadata service

Additionally, the following project properties can be specified that take precedence over the AWS SDK:
* awsAccessKeyId
* awsSecretAccessKey

The secret access key (token) must be permitted to create ECR tokens and read/write (as needed) to the repositories.

Contributions
-------------

This is open source software licensed under the Apache License Version 2.0.
Any issues or pull requests are welcome.

Change Log
----------
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
