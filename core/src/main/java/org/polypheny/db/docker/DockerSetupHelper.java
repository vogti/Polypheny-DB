/*
 * Copyright 2019-2024 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.docker;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.docker.exceptions.DockerUserException;
import org.polypheny.db.docker.models.DockerHost;
import org.polypheny.db.docker.models.HandshakeInfo;

@Slf4j
public final class DockerSetupHelper {

    private DockerSetupHelper() {
    }


    private static void tryConnectDirectly( DockerHost host ) throws IOException {
        byte[] serverCertificate;

        try {
            serverCertificate = PolyphenyCertificateManager.loadServerCertificate( "docker", host.hostname() );
        } catch ( IOException e ) {
            throw new IOException( "No valid server certificate present" );
        }

        PolyphenyKeypair kp = PolyphenyCertificateManager.loadClientKeypair( "docker", host.hostname() );
        PolyphenyDockerClient client = new PolyphenyDockerClient( host.hostname(), host.communicationPort(), kp, serverCertificate );
        client.ping();
        client.close();
    }


    public static Optional<HandshakeInfo> newDockerInstance( @NotNull String hostname, @NotNull String alias, @NotNull String registry, int communicationPort, int handshakePort, int proxyPort, boolean startHandshake ) {
        DockerHost host = new DockerHost( hostname, alias, registry, communicationPort, handshakePort, proxyPort );
        if ( DockerManager.getInstance().hasHost( host.hostname() ) ) {
            throw new DockerUserException( "There is already a Docker instance connected to " + hostname );
        }

        if ( DockerManager.getInstance().hasAlias( host.alias() ) ) {
            throw new DockerUserException( "There is already a Docker instance with alias " + alias );
        }

        try {
            tryConnectDirectly( host );
            DockerManager.getInstance().addDockerInstance( host, null );
            return Optional.empty();
        } catch ( IOException e ) {
            return Optional.of( HandshakeManager.getInstance()
                    .newHandshake(
                            host,
                            () -> DockerManager.getInstance().addDockerInstance( host, null ),
                            startHandshake
                    )
            );
        }

    }


    public static DockerUpdateResult updateDockerInstance( int id, String hostname, String alias, String registry ) {
        Optional<DockerInstance> maybeDockerInstance = DockerManager.getInstance().getInstanceById( id );

        if ( maybeDockerInstance.isEmpty() ) {
            return new DockerUpdateResult( "No docker instance with that id" );
        }

        DockerInstance dockerInstance = maybeDockerInstance.get();
        DockerHost newHost = new DockerHost( hostname, alias, registry, dockerInstance.getHost().communicationPort(), dockerInstance.getHost().handshakePort(), dockerInstance.getHost().proxyPort() );

        boolean hostChanged = !dockerInstance.getHost().hostname().equals( newHost.hostname() );
        DockerManager.getInstance().updateDockerInstance( id, hostname, alias, registry );

        if ( hostChanged && !dockerInstance.isConnected() ) {
            HandshakeManager.getInstance().newHandshake(
                    newHost,
                    () -> DockerManager.getInstance().getInstanceById( id ).ifPresent( DockerInstance::reconnect ),
                    true
            );
            return new DockerUpdateResult( dockerInstance, true );
        } else {
            return new DockerUpdateResult( dockerInstance, false );
        }
    }


    public static HandshakeInfo reconnectToInstance( int id ) {
        Optional<DockerInstance> maybeDockerInstance = DockerManager.getInstance().getInstanceById( id );
        if ( maybeDockerInstance.isEmpty() ) {
            throw new DockerUserException( "No instance with that id" );
        }

        DockerInstance dockerInstance = maybeDockerInstance.get();

        return HandshakeManager.getInstance().newHandshake(
                dockerInstance.getHost(),
                () -> DockerManager.getInstance().getInstanceById( id ).ifPresent( DockerInstance::reconnect ),
                true
        );
    }


    public static void removeDockerInstance( int id ) {
        Optional<DockerInstance> maybeDockerInstance = DockerManager.getInstance().getInstanceById( id );

        if ( maybeDockerInstance.isEmpty() ) {
            throw new DockerUserException( 404, "No Docker instance with that id" );
        }

        DockerInstance dockerInstance = maybeDockerInstance.get();
        try {
            if ( dockerInstance.hasContainers() ) {
                throw new DockerUserException( "Docker instance still in use by at least one container" );
            }
        } catch ( IOException e ) {
            log.info( "Failed to retrieve list of docker containers " + e );
        }

        HandshakeManager.getInstance().cancelHandshakes( dockerInstance.getHost().hostname() );
        DockerManager.getInstance().removeDockerInstance( id );
    }


    static public final class DockerSetupResult {

        @Getter
        private String error = "";
        private HandshakeInfo handshake = null;
        @Getter
        private boolean success = false;


        private DockerSetupResult( boolean success ) {
            this.success = success;
        }


        private DockerSetupResult( HandshakeInfo handshake ) {
            this.handshake = handshake;
        }


        private DockerSetupResult( String error ) {
            this.error = error;
        }


        public Map<String, Object> getMap() {
            return Map.of(
                    "error", error,
                    "handshake", handshake,
                    "success", success
            );
        }

    }


    static public final class DockerUpdateResult {

        private String error = "";
        private HandshakeInfo handshake = null;
        private Map<String, Object> instance = Map.of();


        private DockerUpdateResult( String err ) {
            this.error = err;
        }


        private DockerUpdateResult( DockerInstance dockerInstance, boolean handshake ) {
            this.instance = dockerInstance.getMap();

            if ( handshake ) {
                // TODO: Fix
                // this.handshake = HandshakeManager.getInstance().getHandshake( dockerInstance.getHost().hostname() );
            }
        }


        public Map<String, Object> getMap() {
            return Map.of(
                    "error", error,
                    "handshake", handshake,
                    "instance", instance
            );
        }

    }


    static public final class DockerReconnectResult {

        @Getter
        private String error = "";
        private Map<String, String> handshake = Map.of();


        private DockerReconnectResult( String error ) {
            this.error = error;
        }


        private DockerReconnectResult( Map<String, String> handshake ) {
            this.handshake = handshake;
        }


        public Map<String, Object> getMap() {
            return Map.of(
                    "error", error,
                    "handshake", handshake
            );
        }

    }

}
