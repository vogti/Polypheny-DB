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

package ch.unibas.dmi.dbis.polyphenydb.information;


import java.util.UUID;


/**
 * An Information object that will displayed as a progress bar in the UI
 */
public class InformationProgress extends Information {

    private String label;
    private int value;
    private ProgressColor color = ProgressColor.DYNAMIC;
    private int min = 0;
    private int max = 100;


    /**
     * Constructor
     *
     * @param group The group this information object belongs to
     * @param label Label that will be displayed near the progress bar
     * @param value Value of the progress bar
     */
    public InformationProgress( final InformationGroup group, final String label, final int value ) {
        this( group.getId(), label, value );
    }


    /**
     * Constructor
     *
     * @param groupId The id of the group this information object belongs to
     * @param label Label that will be displayed near the progress bar
     * @param value Value of the progress bar
     */
    public InformationProgress( final String groupId, final String label, final int value ) {
        this( UUID.randomUUID().toString(), groupId, label, value );
    }


    /**
     * Constructor
     *
     * @param id Id of this Information object
     * @param groupId Group to which this information object belongs to
     * @param label Label that will be displayed near the progress bar
     * @param value Value of the progress bar
     */
    public InformationProgress( final String id, final String groupId, final String label, final int value ) {
        super( id, groupId );
        this.label = label;
        this.value = value;
    }


    /**
     * Set the color of this progress-bar.
     * The default color is {@link ProgressColor#DYNAMIC}
     *
     * @param color Color of the progress bar
     */
    public InformationProgress setColor( final ProgressColor color ) {
        this.color = color;
        return this;
    }


    /**
     * Set the minimum value of this progress bar.
     *
     * @param min minimum value
     */
    public InformationProgress setMin( final int min ) {
        this.min = min;
        return this;
    }


    /**
     * Set the maximum value of this progress bar.
     *
     * @param max maximum value
     */
    public InformationProgress setMax( final int max ) {
        this.max = max;
        return this;
    }


    /**
     * Update the value of the current state of a progress bar.
     *
     * @param value New value for the progress bar
     */
    public void updateProgress( final int value ) {
        this.value = value;
        notifyManager();
    }


    /**
     * Define the color of a progress bar.
     * DYNAMIC: changes with increasing value, from blue, to green, to yellow, to red.
     */
    public enum ProgressColor {
        DYNAMIC,
        BLUE,
        GREEN,
        YELLOW,
        RED,
        BLACK
    }


}
