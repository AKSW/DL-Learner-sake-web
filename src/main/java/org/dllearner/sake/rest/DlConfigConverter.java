package org.dllearner.sake.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Simon Bin on 16-8-22.
 */
public class DlConfigConverter {
	private StringBuffer out;
	public String convert(Map in) {
		out = new StringBuffer();
		visit(in, true);
		return out.toString();
	}

	private void visit(Map in) {
		Iterator it = in.entrySet().iterator();
		out.append("[\n");
		while (it.hasNext()) {
			Map.Entry e = (Map.Entry) it.next();
			Object key = e.getKey();
			Object value = e.getValue();
			out.append("(");
			visit(key);
			out.append(",");
			visit(value);
			out.append(")");
			if (it.hasNext()) {
				out.append(",");
			}
			out.append("\n");
		}
		out.append("]");
	}

	private void visit(List in) {
		out.append("{ \n");
		Iterator it = in.iterator();
		while (it.hasNext()) {
			Object o = it.next();
			visit(o);
			if (it.hasNext()) {
				out.append(",");
			}
			out.append("\n");
		}
		out.append("}");
	}

	private void visit(Number in) {
		if (in instanceof Integer
				&& in.intValue() >= 0) {
			out.append(in);
		} else if (in instanceof Long
				&& in.longValue() >= 0) {
			out.append(in);
		} else if (in instanceof Double) {
			DecimalFormat df = new DecimalFormat("#.###########", DecimalFormatSymbols.getInstance(Locale.ROOT));
			String format = df.format(in.doubleValue());
			if (in.doubleValue() >= 0) out.append(format);
			else visit(format);
		} else {
			visit(in.toString());
		}
	}

	private void visit(Boolean in) {
		out.append(in.booleanValue());
	}

	private void visit(String in) {
		if (in.startsWith("#")) {
			// reference to object
			out.append(in.substring(1));
		} else {
			out.append("\"");
			String encoded = null;
			try {
				encoded = URLEncoder.encode(in, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				encoded = in;
			}
			out.append(encoded);
			out.append("\"");
		}
	}

	private void visit(Map in, boolean top) {
		visit(in, top, null);
	}

	private void visit(Map in, boolean top, String prefix) {
		Iterator it = in.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry e = (Map.Entry) it.next();
			Object key = e.getKey();
			Object value = e.getValue();
			if (top
					&& value instanceof Map
					&& ((Map)value).containsKey("type")) {
				out.append("\n");
				visit((Map) value, false, (String)key);
			} else if ("comment".equals(key)) {
				DlConfigConverter commentConv = new DlConfigConverter();
				commentConv.out = new StringBuffer();
				if (value instanceof Map) {
					commentConv.visit((Map) value, top, prefix);
				} else {
					commentConv.visit(value);
				}
				for (String s : commentConv.out.toString().split("\n")) {
					out.append("// "); out.append(s); out.append("\n");
				}
			} else {
				if (prefix != null) {
					out.append(prefix);
					out.append(".");
				}
				out.append(key); out.append(" = ");
				visit(value);
				out.append("\n");
			}
		}
	}

	private void visit(Object obj) {
		if (obj instanceof List) {
			visit((List) obj);
		} else if (obj instanceof Map) {
			visit((Map) obj);
		} else if (obj instanceof Boolean) {
			visit((Boolean) obj);
		} else if (obj instanceof Number) {
			visit((Number) obj);
		} else if (obj instanceof String) {
			visit((String) obj);
		} else {
			throw new RuntimeException("conversion not implemented: " + obj.getClass());
		}
	}

}
