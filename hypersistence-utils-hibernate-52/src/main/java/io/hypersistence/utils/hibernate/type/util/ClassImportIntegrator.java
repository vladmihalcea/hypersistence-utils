package io.hypersistence.utils.hibernate.type.util;

import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.util.List;

/**
 * The {@link ClassImportIntegrator} implements the Hibernate {@link Integrator} contract
 * and allows you to provide a {@link List} of classes to be imported using their simple name.
 *
 * For instance, you could use a DTO simple class name, instead of the fully-qualified name
 * when building a constructor expression in a JPQL query.
 *
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/dto-projection-jpa-query/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class ClassImportIntegrator implements Integrator {

	private static final String DOT = ".";

	private final List<? extends Class> classImportList;

	private String excludedPath;

	/**
	 * Builds a new integrator that can register the provided classes.
	 *
	 * @param classImportList list of classes to be imported
	 */
	public ClassImportIntegrator(List<? extends Class> classImportList) {
		this.classImportList = classImportList;
	}

	/**
	 * Exclude the provided parent path and register the remaining relative path.
	 * If the {@link #excludedPath} is not set, then the package is excluded and
	 * only the simple class name is registered.
	 *
	 * For instance, if you provide the {@code io.hypersistence.utils.hibernate.type} path,
	 * and register a class whose fully-qualified name is {@code io.hypersistence.utils.hibernate.type.json.PostDTO},
	 * then the class is going to be registered under the {@code json.PostDTO} alias.
	 *
	 * @param path path to be excluded.
	 * @return the {@link ClassImportIntegrator} object reference
	 */
	public ClassImportIntegrator excludePath(String path) {
		this.excludedPath = path.endsWith(DOT) ? path : path + DOT;
		return this;
	}

	/**
	 * Register the provided classes by their simple name or relative package and class name.
	 *
	 * @param metadata metadata
	 * @param sessionFactory Hibernate session factory
	 * @param serviceRegistry Hibernate service registry
	 */
	@Override
	public void integrate(
			Metadata metadata,
			SessionFactoryImplementor sessionFactory,
			SessionFactoryServiceRegistry serviceRegistry) {
		for(Class classImport : classImportList) {
			String key;
			if(excludedPath != null) {
				key = classImport.getName().replace(excludedPath, "");
			} else {
				key = classImport.getSimpleName();
			}

			metadata.getImports().put(
				key,
				classImport.getName()
			);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void disintegrate(
			SessionFactoryImplementor sessionFactory,
			SessionFactoryServiceRegistry serviceRegistry) {}
}