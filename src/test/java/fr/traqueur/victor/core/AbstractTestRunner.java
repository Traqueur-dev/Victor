package fr.traqueur.victor.core;

import fr.traqueur.victor.VictorBuilder;
import org.junit.jupiter.api.Nested;

public abstract class AbstractTestRunner {

    protected abstract VictorBuilder configureVictor();

    @Nested
    class Crud extends AbstractCrudTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class DynamicQuery extends AbstractDynamicQueryTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class Relationships extends AbstractRelationshipTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class Embedded extends AbstractEmbeddedTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class Migration extends AbstractMigrationTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class TypeMapping extends AbstractTypeMappingTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class ServiceLayer extends AbstractUserServiceTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }

    @Nested
    class Transactions extends AbstractTransactionTest {
        @Override
        protected VictorBuilder configureVictor() {
            return AbstractTestRunner.this.configureVictor();
        }
    }
}