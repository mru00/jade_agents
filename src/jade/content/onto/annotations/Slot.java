/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*****************************************************************/

package jade.content.onto.annotations;

//#J2ME_EXCLUDE_FILE

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows to specify in the ontological schema the <code>mandatory</code> and <code>name</code> attributes of the slot.<br>
 * The annotation is to be applied to the getter method.
 *
 * @author Paolo Cancedda
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Slot {
	String USE_METHOD_NAME = "__USE_METHOD_NAME__";
	String NULL = "__NULL__";

	String name() default USE_METHOD_NAME;
	String documentation() default NULL;
	int position() default -1;
	boolean mandatory() default false;
	String defaultValue() default NULL; 
	String regex() default NULL;
	String[] permittedValues() default {};
}
