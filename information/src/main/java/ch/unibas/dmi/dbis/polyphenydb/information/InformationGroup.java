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

package ch.unibas.dmi.dbis.polyphenydb.information;


import ch.unibas.dmi.dbis.polyphenydb.information.exception.InformationRuntimeException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * An InformationGroup contains multiple Information object that will be rendered together in the UI.
 */
public class InformationGroup {

    /**
     * Unique id for an InformationGroup.
     */
    private final String id;


    /**
     * The id of the page this group belongs to.
     */
    private final String pageId;


    /**
     * The name of this group
     */
    private String name; // title


    /**
     * The color of this group. This is used in the UI.
     */
    private GroupColor color;


    /**
     * Groups with lower uiOrder will be rendered first in the UI. The groups with no uiOrder (0) are rendered last.
     */
    private int uiOrder;


    /**
     * Is true, if the group was created implicit. If it will be created explicit, additional information (color/uiOrder) will be added.
     */
    private boolean implicit = false;


    /**
     * A Map of Information objects that belong to this group.
     */
    private final ConcurrentMap<String, Information> informationObjects = new ConcurrentHashMap<>();


    /**
     * Constructor
     *
     * @param id Id of this group
     * @param pageId Id of the page this group belongs to
     */
    public InformationGroup( final String id, final String pageId, final String name ) {
        this.id = id;
        this.pageId = pageId;
        this.name = name;
    }


    /**
     * Constructor which generates a unique id.
     *
     * @param pageId Id of the page this group belongs to
     */
    public InformationGroup( final String pageId, final String name ) {
        this.id = UUID.randomUUID().toString();
        this.pageId = pageId;
        this.name = name;
    }


    /**
     * Constructor which generates a unique id.
     *
     * @param page The page this group belongs to
     */
    public InformationGroup( final InformationPage page, final String name ) {
        this( page.getId(), name );
    }


    /**
     * If you want the group to have a certain color in the UI, you can set it here.
     *
     * @param color Color for this group
     */
    public InformationGroup setColor( final GroupColor color ) {
        this.color = color;
        return this;
    }


    /**
     * Add an information object to this group.
     */
    public void addInformation( final Information... infos ) {
        for ( Information i : infos ) {
            if ( !i.getGroup().equals( this.id ) ) {
                throw new InformationRuntimeException( "You are trying to add an information to a group where it does not belong to." );
            }
            this.informationObjects.put( i.getId(), i );
        }
    }


    /**
     * Groups with lower uiOrder will be rendered first in the UI. The groups with no uiOrder (0) are rendered last.
     *
     * @param order An int > 0
     */
    public InformationGroup setOrder( final int order ) {
        this.uiOrder = order;
        return this;
    }


    /**
     * Return the id for the group
     *
     * @return Id of the group
     */
    public String getId() {
        return id;
    }


    /**
     * Return the id of the page to which this group belongs to.
     *
     * @return The page id of this group
     */
    public String getPageId() {
        return pageId;
    }


    /**
     * Check if group was created implicitly.
     */
    public boolean isImplicit() {
        return implicit;
    }


    /**
     * Setter for the implicit field.
     *
     * @param implicit true if the group was created implicitly
     */
    public InformationGroup setImplicit( final boolean implicit ) {
        this.implicit = implicit;
        return this;
    }


    /**
     * If the InformationGroup was created implicitly, it can be overwritten with an explicitly created InformationGroup.
     */
    public void overrideWith ( final InformationGroup group ) {
        if( ! this.implicit ){
            throw new InformationRuntimeException( "Explicitly created pages are not allowed to be overwritten." );
        }else if( group.isImplicit() ){
            throw new InformationRuntimeException( "A page cannot be overwritten by an implicitly created page." );
        }
        this.color = group.color;
        this.uiOrder = group.uiOrder;
        this.informationObjects.putAll( group.informationObjects );
        this.implicit = false;
    }

}
