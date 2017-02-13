package com.patdouble.gradle.awsecr

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ecr.AmazonECRClient
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.AbstractReactiveStreamsTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Input

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
    String awsAccessKeyId

    @Input
    String awsSecretAccessKey

    Logger logger
    String credFileDirectory

    @Override
    void runReactiveStream() {

        def credFile = new File("${credFileDirectory}/ecr${Math.abs((awsAccessKeyId + awsSecretAccessKey + registryId).hashCode())}.properties")

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

        AmazonECRClient ecrClient = new AmazonECRClient(new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey))

        // honor region set in registryUrl so correct signer is used
        ecrClient.setEndpoint(registryUrl.replaceAll( '^.*(ecr.*)$', '$1' ) )

        GetAuthorizationTokenResult tokens = ecrClient.getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(registryId))

        if (!tokens.authorizationData) {
            throw new GradleException("Could not get ECR token: ${tokens.sdkHttpMetadata.httpStatusCode}")
        }

        def ecrCreds = new String(tokens.authorizationData.first().authorizationToken.decodeBase64(), 'US-ASCII').split(':')

        Properties props = new Properties()
        props.setProperty('username', ecrCreds[0])
        props.setProperty('password', ecrCreds[1])
        props.setProperty('expiresAt', String.valueOf(tokens.authorizationData.first().expiresAt.time))
        credFile.parentFile.mkdirs()
        credFile.withOutputStream { props.store(it, "ECR Credentials for ${awsAccessKeyId} @ ${registryUrl}") }

        registryCredentials.with {
            url = registryUrl
            username = ecrCreds[0]
            password = ecrCreds[1]
        }
    }
}
