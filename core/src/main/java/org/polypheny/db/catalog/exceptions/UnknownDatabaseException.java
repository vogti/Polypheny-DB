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

package org.polypheny.db.catalog.exceptions;


public class UnknownDatabaseException extends CatalogException {


    public UnknownDatabaseException( String databaseName ) {
        super( "There is no database with name " + databaseName );
    }


    public UnknownDatabaseException( long databaseId ) {
        super( "There is no database with the id " + databaseId );
    }
}
