package com.patdouble.gradle.awsecr

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.AmazonECRClient
import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.AbstractReactiveStreamsTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class PopulateECRCredentials extends AbstractReactiveStreamsTask implements RegistryCredentialsAware {

    @Input
    DockerRegistryCredentials registryCredentials

    @Input
    String repository

    @Input
    String registryId

    @Input
    String registryUrl

    @Input
    @Optional
    String awsAccessKeyId

    @Input
    @Optional
    String awsSecretAccessKey

    Logger logger
    String credFileDirectory

    @Override
    void runReactiveStream() {

        def credFile = new File("${credFileDirectory}/ecr${Math.abs((registryId).hashCode())}.properties")

        if (credFile.canRead() && credFile.isFile()) {
            Properties props = new Properties()
            credFile.withInputStream { props.load(it) }
            if (props.get('expiresAt') && Long.parseLong(props.getProperty('expiresAt')) > (System.currentTimeMillis() + 3600000)) {
                logger.info("Using ECR registryCredentials from ${credFile}")
                registryCredentials.with {
                    url = registryUrl
                    username = props.getProperty('username')
                    password = props.getProperty('password')
                }
            }
        }

        //Get the region from the registry URL
        def region = registryUrl.replaceAll(/^.*ecr\.(.*)\.amazonaws.*$/, '$1')

        AmazonECRClientBuilder ecrClientBuilder = AmazonECRClientBuilder.standard().withRegion(region)

        if (awsAccessKeyId != null && awsSecretAccessKey != null)
        {
            ecrClientBuilder.setCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey)))
        }

        GetAuthorizationTokenResult tokens = ecrClientBuilder.build()
                .getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(registryId))

        if (!tokens.authorizationData) {
            throw new GradleException("Could not get ECR token: ${tokens.sdkHttpMetadata.httpStatusCode}")
        }

        def ecrCreds = new String(tokens.authorizationData.first().authorizationToken.decodeBase64(), 'US-ASCII').split(':')

        Properties props = new Properties()
        props.setProperty('username', ecrCreds[0])
        props.setProperty('password', ecrCreds[1])
        props.setProperty('expiresAt', String.valueOf(tokens.authorizationData.first().expiresAt.time))
        credFile.parentFile.mkdirs()
        credFile.withOutputStream { props.store(it, "ECR Credentials @ ${registryUrl}") }

        registryCredentials.with {
            url = registryUrl
            username = ecrCreds[0]
            password = ecrCreds[1]
        }
    }
}
