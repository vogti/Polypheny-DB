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


import ch.unibas.dmi.dbis.polyphenydb.config.Config.ConfigListener;
import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Test;


public class ConfigManagerTest implements ConfigListener {

    private ConfigManager cm;
    private boolean wasRestarted = false;


    static {
        ConfigManager cm = ConfigManager.getInstance();
        WebUiPage p = new WebUiPage( "p", "page1", "page1descr" );
        WebUiGroup g1 = new WebUiGroup( "g1", "p" ).withTitle( "group1" );
        WebUiGroup g2 = new WebUiGroup( "g2", "p" ).withDescription( "group2" );

        Config c1 = new ConfigString( "conf.test.2", "text1" ).withUi( "g2", WebUiFormType.TEXT );
        cm.registerConfig( c1 );
        cm.addUiGroup( g2 );
        cm.addUiGroup( g1 );
        cm.addUiPage( p );
    }


    public ConfigManagerTest() {
        //insert config before groups and pages are existing
        cm = ConfigManager.getInstance();
    }

    /*@Test
    public void insertConfigTwice() {

        Config c1 = new ConfigString("conf.text.1", "text1").withUi( 1, WebUiFormType.TEXT );
        Config c2 = new ConfigString("conf.text.1", "text1");

        Config c3 = new ConfigInteger( "double", 2 );
        Config c4 = new ConfigInteger( "double", 2 ).withUi( 1, WebUiFormType.NUMBER );

        //inserting configs before groups and pages are existing
        cm.registerConfig( c1 );
        cm.registerConfig( c2 );
        cm.registerConfig( c3 );
        cm.registerConfig( c4 );

        cm.getConfig( "conf.text.1" ).setString( "config1" );
        //c1.setString( "config1" );//wouldn't work
        cm.getConfig( "double" ).setInt( 22 );
        Assert.assertEquals( "config1", cm.getConfig( "conf.text.1" ).getString() );
        Assert.assertEquals( 1, cm.getConfig( "double" ).getWebUiGroup() );

    }*/


    @Test
    public void javaValidation () {
        Config c5 = new ConfigInteger( "java.int.validation", 10 ).withJavaValidation( a -> a < 10 ).withUi( "g2", WebUiFormType.NUMBER );
        Config c6 = new ConfigInteger( "java.number.validation", 10 ).withJavaValidation( a -> a < 10 ).withUi( "g2", WebUiFormType.NUMBER );

        cm.registerConfig( c5 );
        cm.registerConfig( c6 );

        cm.getConfig( "java.int.validation" ).setInt( 2 );
        cm.getConfig( "java.int.validation" ).setInt( 20 );
        int a = cm.getConfig( "java.int.validation" ).getInt();
        Assert.assertEquals( 2, a );
        cm.getConfig( "java.number.validation" ).setInt( 3 );
        cm.getConfig( "java.number.validation" ).setInt( 20 );
        //Assert.assertEquals( 2, cm.getConfig( "java.number.validation" ).getInt() );

        System.out.println( cm.getPage( "p" ) );
    }


    @Test
    public void configTypes() {
        Config c2 = new ConfigString( "type.string", "string" );
        Config c3 = new ConfigBoolean( "type.boolean", true );
        Config c4 = new ConfigInteger( "type.integer", 11 );
        Config c5 = new ConfigLong( "type.long", 100 );
        Config c6 = new ConfigDouble( "type.double", 1.01 );
        Config c7 = new ConfigDecimal( "type.decimal", new BigDecimal( 1.0001 ) );

        c2.setString( "string" );
        c3.setBoolean( true );
        c4.setInt( 10 );
        c5.setLong( 11 );
        c6.setDouble( 10.1 );
        c7.setDecimal( new BigDecimal( 3.14 ) );

        cm.registerConfigs( c2, c3, c4, c5, c6, c7 );

        Assert.assertEquals( "string", cm.getConfig( "type.string" ).getString() );
        Assert.assertTrue( cm.getConfig( "type.boolean" ).getBoolean() );
        Assert.assertEquals( 10, (int) cm.getConfig( "type.integer" ).getInt() );
        Assert.assertEquals( 11, (long) cm.getConfig( "type.long" ).getLong() );
        Assert.assertEquals( 10.1, cm.getConfig( "type.double" ).getDouble(), 0.0001 );
        Assert.assertEquals( new BigDecimal( 3.14 ), cm.getConfig( "type.decimal" ).getDecimal() );

    }


    @Test
    public void isNotified() {

        class ConfigObserver implements ConfigListener {

            private boolean wasNotified = false;


            public void restart( Config c ) {
                this.wasNotified = true;
            }


            public void onConfigChange( Config c ) {
                this.wasNotified = true;
            }


            public boolean wasNotified() {
                return this.wasNotified;
            }
        }

        ConfigObserver o1 = new ConfigObserver();
        ConfigObserver o2 = new ConfigObserver();
        ConfigBoolean willChange = new ConfigBoolean( "will.change", true );
        cm.registerConfig( willChange );
        cm.getConfig( "will.change" ).addObserver( o1 );
        cm.getConfig( "will.change" ).addObserver( o2 );
        cm.getConfig( "will.change" ).removeObserver( o1 );
        cm.getConfig( "will.change" ).setBoolean( true );
        Assert.assertTrue( o2.wasNotified() );
        Assert.assertFalse( o1.wasNotified() );

    }


    @Test
    public void isRestarted() {
        Config c = new ConfigString( "test.restart", "restart" ).setRequiresRestart( true ).addObserver( this );
        cm.registerConfig( c );
        cm.getConfig( c.getKey() ).setString( "someValue" );
        Assert.assertEquals( true, this.wasRestarted );
    }


    public void onConfigChange( Config c ) {
        System.out.println( "configChange: " + c.getKey() );
    }


    public void restart( Config c ) {
        System.out.println( "Config " + c.getKey() + " triggered restart;" );
        this.wasRestarted = true;
    }

}
