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

package ch.unibas.dmi.dbis.polyphenydb.config;

// TODO MV: Missing methods, missing JavaDoc


public class ConfigArray extends Config {

    private ConfigScalar[] array;


    public ConfigArray( final String key, final ConfigScalar[] array ) {
        super( key );
        this.array = array;
    }


    public ConfigArray( final String key, final String description, final ConfigScalar[] array ) {
        super( key, description );
        this.array = array;
    }


    @Override
    void setObject( final Object value ) {
        this.array = (ConfigScalar[]) value;
        notifyConfigListeners();
    }


    @Override
    public int[] getIntArray() {
        int[] out = new int[array.length];
        for ( int i = 0; i < out.length; i++ ) {
            out[i] = array[i].getInt();
        }
        return out;
    }


    @Override
    public double[] getDoubleArray() {
        double[] out = new double[array.length];
        for ( int i = 0; i < out.length; i++ ) {
            out[i] = array[i].getDouble();
        }
        return out;
    }

    // TODO MV: Missing setters

}
