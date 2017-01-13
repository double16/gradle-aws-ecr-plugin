package com.patdouble.gradle.awsecr

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ecr.AmazonECRClient
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.regex.Pattern

class AwsecrPlugin implements Plugin<Project> {
    private static final Pattern AWS_ECR_URL = ~/https:\/\/([0-9A-Za-z]+)\.dkr\.ecr\.[a-zA-Z0-9-]+\.amazonaws\.com/

    Project project

    @Override
    void apply(Project project) {
        assert project
        this.project = project

        project.afterEvaluate {
            project.tasks.withType(RegistryCredentialsAware) { task ->
                configureRegistryCredentials(task)
            }
        }
    }

    protected String findRepository(RegistryCredentialsAware registryCredentialsAware) {
        if (registryCredentialsAware.hasProperty('repository')) {
            return registryCredentialsAware['repository'] as String
        } else if (registryCredentialsAware.hasProperty('imageName')) {
            return registryCredentialsAware['imageName'] as String
        } else if (registryCredentialsAware.hasProperty('tag')) {
            return registryCredentialsAware['tag'] as String
        }
        project.logger.info("Skipping docker credentials for ${registryCredentialsAware} because we do not know how to find a repository for this type (${registryCredentialsAware.class}")
        null
    }

    /**
     * Extract the ECR repository ID and URL
     * @return map containing 'id' and 'url' keys or empty if repository URL is unknown
     */
    protected Map<String, String> extractEcrInfo(CharSequence repository) {
        def m = AWS_ECR_URL.matcher(repository)
        if (!m) {
            return [:]
        }

        ['id' : m.group(1),
         'url': m.group(0)]
    }

    protected void configureRegistryCredentials(RegistryCredentialsAware registryCredentialsAware) {
        String repository = findRepository(registryCredentialsAware)
        if (!repository) {
            return
        }

        def info = extractEcrInfo(repository)
        if (!info) {
            project.logger.info("Skipping docker credentials for ${registryCredentialsAware} because repository '${repository}' is not AWS ECR")
            return
        }

        String registryId = info.id
        String registryUrl = info.url
        project.logger.info("Found ECR registry account ID ${registryId} at ${registryUrl}")
        registryCredentialsAware.registryCredentials = findEcrCredentials(registryId, registryUrl)
    }

    /**
     * Determine the AWS ECR credentials, if present.
     * @return
     */
    protected DockerRegistryCredentials findEcrCredentials(String registryId, String registryUrl) {
        def awsAccessKeyId = System.getenv('AWS_ACCESS_KEY_ID')
        def awsSecretAccessKey = System.getenv('AWS_SECRET_ACCESS_KEY')
        if (!awsAccessKeyId || !awsSecretAccessKey) {
            throw new GradleException("AWS ECR registry requires AWS account credentials configured in environment AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY")
        }

        def credFile = project.rootProject.file(".gradle/ecr${Math.abs((awsAccessKeyId + awsSecretAccessKey + registryId).hashCode())}.properties")

        if (credFile.canRead() && credFile.isFile()) {
            Properties props = new Properties()
            credFile.withInputStream { props.load(it) }
            if (props.get('expiresAt') && Long.parseLong(props.getProperty('expiresAt')) > (System.currentTimeMillis() + 3600000)) {
                project.logger.info("Using ECR credentials from ${credFile}")
                return new DockerRegistryCredentials(url: registryUrl, username: props.getProperty('username'), password: props.getProperty('password'))
            }
        }

        AmazonECRClient ecrClient = new AmazonECRClient(new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey))

        // honor region set in registryUrl so correct signer is used
        ecrClient.setEndpoint( registryUrl.replaceAll( '^.*(ecr.*)$', '$1' ) )

        GetAuthorizationTokenResult tokens = ecrClient.getAuthorizationToken(new GetAuthorizationTokenRequest().withRegistryIds(registryId))
        if (tokens.authorizationData) {
            def ecrCreds = new String(tokens.authorizationData.first().authorizationToken.decodeBase64(), 'US-ASCII').split(':')

            Properties props = new Properties()
            props.setProperty('username', ecrCreds[0])
            props.setProperty('password', ecrCreds[1])
            props.setProperty('expiresAt', String.valueOf(tokens.authorizationData.first().expiresAt.time))
            credFile.parentFile.mkdirs()
            credFile.withOutputStream { props.store(it, "ECR Credentials for ${awsAccessKeyId} @ ${registryUrl}") }

            return new DockerRegistryCredentials(url: registryUrl, username: ecrCreds[0], password: ecrCreds[1])
        }

        throw new GradleException("Could not get ECR token: ${tokens.sdkHttpMetadata.httpStatusCode}")
    }
}
