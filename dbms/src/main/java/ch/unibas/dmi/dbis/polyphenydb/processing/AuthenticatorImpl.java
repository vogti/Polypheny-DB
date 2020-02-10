/*
 * Copyright 2019-2020 The Polypheny Project
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

package ch.unibas.dmi.dbis.polyphenydb.processing;


import ch.unibas.dmi.dbis.polyphenydb.AuthenticationException;
import ch.unibas.dmi.dbis.polyphenydb.Authenticator;
import ch.unibas.dmi.dbis.polyphenydb.catalog.CatalogManagerImpl;
import ch.unibas.dmi.dbis.polyphenydb.catalog.entity.CatalogUser;
import ch.unibas.dmi.dbis.polyphenydb.catalog.exceptions.GenericCatalogException;
import ch.unibas.dmi.dbis.polyphenydb.catalog.exceptions.UnknownUserException;


/**
 *
 */
public class AuthenticatorImpl implements Authenticator {

    @Override
    public CatalogUser authenticate( final String username, final String password ) throws AuthenticationException {
        try {
            CatalogUser catalogUser = CatalogManagerImpl.getInstance().getUser( username );
            if ( catalogUser.password.equals( password ) ) {
                return catalogUser;
            } else {
                throw new AuthenticationException( "Wrong password for user '" + username + "'!" );
            }
        } catch ( UnknownUserException | GenericCatalogException e ) {
            throw new AuthenticationException( e );
        }
    }
}
