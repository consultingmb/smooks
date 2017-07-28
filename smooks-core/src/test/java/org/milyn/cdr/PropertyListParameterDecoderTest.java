package org.milyn.cdr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.milyn.cdr.annotation.Configurator;

import java.util.Properties;

public class PropertyListParameterDecoderTest {

	@Test
	public void test_decodeValue() {
		SmooksResourceConfiguration config = new SmooksResourceConfiguration("x", "x");
		PropertyListParameterDecoder propDecoder = (PropertyListParameterDecoder) Configurator.configure(new PropertyListParameterDecoder(), config);
		
		Properties properties = (Properties) propDecoder.decodeValue("x=111\ny=222");
		assertEquals(2, properties.size());
		assertEquals("111", properties.getProperty("x"));
		assertEquals("222", properties.getProperty("y"));
	}

}
