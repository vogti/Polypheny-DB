/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package ch.unibas.dmi.dbis.polyphenydb.catalog.entity;


import ch.unibas.dmi.dbis.polyphenydb.catalog.Catalog.Collation;
import ch.unibas.dmi.dbis.polyphenydb.catalog.Catalog.Encoding;
import ch.unibas.dmi.dbis.polyphenydb.catalog.Catalog.TableType;
import ch.unibas.dmi.dbis.polyphenydb.catalog.InternalName;
import lombok.Getter;
import lombok.NonNull;


/**
 *
 */
@Getter
public final class CatalogTable extends AbstractCatalogEntity {

    private static final long serialVersionUID = -436870626194775370L;

    private final int owner;
    private Encoding encoding;
    private Collation collation;
    private final TableType type;
    private String definition;
    private InternalName chunkColumn;
    private Integer chunkSize;


    public CatalogTable( @NonNull final InternalName internalName, @NonNull final String name, final int owner, Encoding encoding, Collation collation, @NonNull final TableType type, String definition, InternalName chunkColumn, Integer chunkSize ) {
        super( internalName, name );
        this.owner = owner;
        this.encoding = encoding;
        this.collation = collation;
        this.type = type;
        this.definition = definition;
        this.chunkColumn = chunkColumn;
        this.chunkSize = chunkSize;
    }


}
