package io.kestra.core.runners.pebble.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class UniqFilterTest {

	private PebbleEngine pebbleEngine;
	
	@BeforeEach
	void setUp() {
		pebbleEngine = new PebbleEngine.Builder()
				.extension(new AbstractExtension() {
					public Map<String, Filter> getFilters() {
						Map<String, Filter> filters = new HashMap<>();
						filters.put("uniq", new UniqFilter());
						return filters;
					}
				}).build();
	}
	
	@Test
	void testUniqFilter() throws Exception {
		List<Integer> inputList = Arrays.asList(1,2,2,3,1,4,4);
		String templateContent = "{{ inputList | uniq }}"; 
		PebbleTemplate template = pebbleEngine.getTemplate(templateContent);
		
		Map<String, Object> context = new HashMap<>();
		context.put("inputList", inputList);
		
		StringWriter writer = new StringWriter();
		template.evaluate(writer, context);
		
		String renderedOutput = writer.toString();
		
		assertEquals("[1, 2, 3, 4]", renderedOutput);
	}
}
