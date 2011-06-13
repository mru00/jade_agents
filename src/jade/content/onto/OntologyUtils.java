package jade.content.onto;

//#J2ME_EXCLUDE_FILE
//#APIDOC_EXCLUDE_FILE

import java.util.Iterator;

import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.schema.ObjectSchema;

public class OntologyUtils {

	private static void dumpSchemas(Ontology ontology, Iterator iter, String prefix) throws OntologyException {
		String name;
		ObjectSchema os;
		while (iter.hasNext()) {
			name = (String) iter.next();
			os = ontology.getSchema(name);
			System.out.print("  " + prefix + " " + name);
			ObjectSchema[] superSchemas = os.getSuperSchemas();
			if (superSchemas != null && superSchemas.length > 0) {
				System.out.print(" [ superschemas: ");
				for (int j = 0; j < superSchemas.length; j++) {
					System.out.print(superSchemas[j]+" ");
				}
				System.out.print("]");
			}
			System.out.println(" ::= {");
			String[] names = os.getNames();
			for (int i = 0; i < names.length; i++) {
				System.out.print("    " + names[i] + ": ");
				ObjectSchema schema = os.getSchema(names[i]);
				if (schema == null) {
					System.out.println("ERROR: no schema!");
				} else {
					System.out.println(schema.getTypeName());
				}
			}
			System.out.println("  }");
		}
	}

	public static void exploreOntology(Ontology ontology) throws OntologyException {
		System.out.println("\n\nOntology \"" + ontology.getName() + "\"\n");
		System.out.println("Concepts:");
		dumpSchemas(ontology, ontology.getConceptNames().iterator(), "concept");
		System.out.println("\nPredicates:");
		dumpSchemas(ontology, ontology.getPredicateNames().iterator(), "predicate");
		System.out.println("\nActions:");
		dumpSchemas(ontology, ontology.getActionNames().iterator(), "action");
	}
}
