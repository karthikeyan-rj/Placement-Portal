package com.college.placement.resume.analysis;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts a controlled, normalised technical skill set from resume text using
 * a fixed variant dictionary. Variants map to a canonical skill name ("SpringBoot",
 * "Spring Boot", "spring-boot" --> "Spring Boot"). No open-ended NLP in this phase.
 */
@Component
public class SkillExtractor {

    private static final Map<String, String> DICTIONARY = buildDictionary();

    /**
     * Extracts matched skills in the canonical dictionary declaration order,
     * de-duplicated. Matching is whole-word/phrase based so "React" does not
     * trigger on "ReactX" and "JS" does not match a random "js" substring.
     */
    public List<String> extract(String text) {
        String haystack = text.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : DICTIONARY.entrySet()) {
            if (containsPhrase(haystack, entry.getKey())) {
                found.add(entry.getValue());
            }
        }
        return new ArrayList<>(found);
    }

    private boolean containsPhrase(String haystack, String phrase) {
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase) + "(?![a-z0-9])");
        return p.matcher(haystack).find();
    }

    private static Map<String, String> buildDictionary() {
        java.util.LinkedHashMap<String, String> d = new java.util.LinkedHashMap<>();

        // Backend / general programming
        d.put("java", "Java");
        d.put("spring boot", "Spring Boot");
        d.put("springboot", "Spring Boot");
        d.put("spring-boot", "Spring Boot");
        d.put("spring security", "Spring Security");
        d.put("spring", "Spring");
        d.put("hibernate", "Hibernate");
        d.put("jpa", "JPA");
        d.put("node.js", "Node.js");
        d.put("nodejs", "Node.js");
        d.put("express", "Express");
        d.put("kotlin", "Kotlin");
        d.put("go/gin", "Go");
        d.put("golang", "Go");
        d.put("python", "Python");
        d.put("django", "Django");
        d.put("flask", "Flask");
        d.put("fastapi", "FastAPI");
        d.put("c++", "C++");
        d.put("c#", "C#");
        d.put(" dotnet", ".NET");
        d.put(".net", ".NET");
        d.put("php", "PHP");
        d.put("laravel", "Laravel");
        d.put("ruby", "Ruby");
        d.put("rust", "Rust");
        d.put("scala", "Scala");

        // Frontend
        d.put("react", "React");
        d.put("reactjs", "React");
        d.put("react.js", "React");
        d.put("typescript", "TypeScript");
        d.put("javascript", "JavaScript");
        d.put("js framework", "JavaScript");
        d.put("vue", "Vue");
        d.put("vue.js", "Vue");
        d.put("angular", "Angular");
        d.put("next.js", "Next.js");
        d.put("nextjs", "Next.js");
        d.put("html", "HTML");
        d.put("css", "CSS");
        d.put("tailwind", "Tailwind CSS");
        d.put("tailwind css", "Tailwind CSS");
        d.put("bootstrap", "Bootstrap");
        d.put("redux", "Redux");
        d.put("react native", "React Native");
        d.put("flutter", "Flutter");
        d.put("dart", "Dart");

        // Data
        d.put("postgresql", "PostgreSQL");
        d.put("postgres", "PostgreSQL");
        d.put("mysql", "MySQL");
        d.put("mongodb", "MongoDB");
        d.put("mongo db", "MongoDB");
        d.put("sql", "SQL");
        d.put("pl/sql", "PL/SQL");
        d.put("redis", "Redis");
        d.put("elasticsearch", "Elasticsearch");
        d.put("kafka", "Kafka");
        d.put("rabbitmq", "RabbitMQ");
        d.put("firebase", "Firebase");
        d.put("oracle", "Oracle DB");
        d.put("sqlite", "SQLite");
        d.put("neo4j", "Neo4j");

        // Cloud / DevOps / tooling
        d.put("aws", "AWS");
        d.put("amazon web services", "AWS");
        d.put("azure", "Azure");
        d.put("gcp", "Google Cloud");
        d.put("google cloud", "Google Cloud");
        d.put("docker", "Docker");
        d.put("kubernetes", "Kubernetes");
        d.put("k8s", "Kubernetes");
        d.put("jenkins", "Jenkins");
        d.put("github actions", "GitHub Actions");
        d.put("gitlab ci", "GitLab CI");
        d.put("terraform", "Terraform");
        d.put("ansible", "Ansible");
        d.put("linux", "Linux");
        d.put("bash", "Bash");
        d.put("shell scripting", "Shell Scripting");
        d.put("nginx", "Nginx");
        d.put("cloudformation", "CloudFormation");

        // Testing / optimisation
        d.put("junit", "JUnit");
        d.put("selenium", "Selenium");
        d.put("cypress", "Cypress");
        d.put("pytest", "pytest");
        d.put("mockito", "Mockito");
        d.put("rest api", "REST API");
        d.put("restful", "REST API");
        d.put("graphql", "GraphQL");
        d.put("apache kafka", "Kafka");
        d.put("swagger", "Swagger/OpenAPI");
        d.put("openapi", "Swagger/OpenAPI");

        // AI / Data science
        d.put("machine learning", "Machine Learning");
        d.put("ml algorithms", "Machine Learning");
        d.put("deep learning", "Deep Learning");
        d.put("tensorflow", "TensorFlow");
        d.put("pytorch", "PyTorch");
        d.put("keras", "Keras");
        d.put("scikit-learn", "scikit-learn");
        d.put("scikit learn", "scikit-learn");
        d.put("nlp", "NLP");
        d.put("natural language processing", "NLP");
        d.put("computer vision", "Computer Vision");
        d.put("opencv", "OpenCV");
        d.put("pandas", "Pandas");
        d.put("numpy", "NumPy");
        d.put("data analysis", "Data Analysis");
        d.put("data visualization", "Data Visualization");
        d.put("power bi", "Power BI");
        d.put("tableau", "Tableau");
        d.put("excel", "Excel");
        d.put("r programming", "R");
        d.put("spark", "Apache Spark");
        d.put("hadoop", "Hadoop");

        // Mobile / other
        d.put("android", "Android");
        d.put("android studio", "Android");
        d.put("kotlin multiplatform", "Kotlin");
        d.put("swift", "Swift");
        d.put("ios", "iOS");
        d.put("git", "Git");
        d.put("github", "GitHub");
        d.put("gitlab", "GitLab");
        d.put("bitbucket", "Git");
        d.put("api", "API Development");
        d.put("microservices", "Microservices");
        d.put("micro service", "Microservices");
        d.put("restful services", "REST API");

        return d;
    }
}