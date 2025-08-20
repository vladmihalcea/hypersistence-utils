package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.Event;
import io.hypersistence.utils.hibernate.type.model.Location;
import io.hypersistence.utils.hibernate.type.model.Participant;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.junit.Test;

import static java.lang.System.identityHashCode;
import static org.junit.Assert.assertEquals;

/**
 * @author Simone Giusso
 */
public class PostgreSQLJsonTypeMergeTest extends AbstractPostgreSQLIntegrationTest {

	@Override
	protected Class<?>[] entities() {
		return new Class<?>[]{
				Event.class,
				Participant.class
		};
	}

	@Test
	public void test() {
		doInJPA(entityManager -> {
			Location location = new Location();
			location.setCountry("Romania");
			location.setCity("Cluj-Napoca");

			Event event = new Event();
			event.setId(1L);
			event.setLocation(location);
			entityManager.persist(event);

			event = entityManager.merge(event);

			assertThatMergeReturnsTheOriginalJsonTypeObject(event.getLocation(), location);
		});
	}

	private void assertThatMergeReturnsTheOriginalJsonTypeObject(Location target, Location original) {
		assertEquals(identityHashCode(original), identityHashCode(target));
	}

}