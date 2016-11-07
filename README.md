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

The plugin will detect the AWS ECR url in docker tasks. Only one AWS account is supported at this time. The AWS credentials must be supplied in the following environment variables:

* AWS_ACCESS_KEY_ID
* AWS_SECRET_ACCESS_KEY

The secret access key (token) must be permitted to create ECR tokens and read/write (as needed) to the repositories. 

Contributions
-------------

This is open source software licensed under the Apache License Version 2.0.
Any issues or pull requests are welcome.
