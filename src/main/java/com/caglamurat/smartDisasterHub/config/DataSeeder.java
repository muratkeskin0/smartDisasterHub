package com.caglamurat.smartDisasterHub.config;

import com.caglamurat.smartDisasterHub.domain.About;
import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.domain.UserRole;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.repository.IAboutRepository;
import com.caglamurat.smartDisasterHub.repository.IRedditAuthorRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRoleRepository;
import com.caglamurat.smartDisasterHub.service.reddit.RedditAuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final IUserRoleRepository userRoleRepository;
    private final IUserRepository userRepository;
    private final IAboutRepository aboutRepository;
    private final IRedditAuthorRepository redditAuthorRepository;
    private final RedditAuthorService redditAuthorService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.firstName:Admin}")
    private String adminFirstName;

    @Value("${app.admin.lastName:User}")
    private String adminLastName;

    @Value("${app.manager.email}")
    private String managerEmail;

    @Value("${app.manager.password}")
    private String managerPassword;

    @Value("${app.manager.firstName:Moderation}")
    private String managerFirstName;

    @Value("${app.manager.lastName:Manager}")
    private String managerLastName;

    @Override
    public void run(String... args) throws Exception {
        seedUserRoles();
        seedAdminUser();
        seedManagerUser();
        seedAbout();
        seedRedditAuthorsFromExistingPosts();
    }

    private void seedRedditAuthorsFromExistingPosts() {
        if (redditAuthorRepository.count() > 0) {
            log.info("reddit_authors already has rows, skipping aggregate rebuild");
            return;
        }
        log.info("reddit_authors is empty — aggregating from reddit_posts...");
        int n = redditAuthorService.rebuildAllFromPosts();
        log.info("Seeded reddit_authors with {} rows", n);
    }

    private void seedUserRoles() {
        log.info("Checking if user roles need to be seeded...");

        if (!userRoleRepository.existsByName(UserRoleType.ADMIN)) {
            UserRole adminRole = UserRole.builder()
                    .name(UserRoleType.ADMIN)
                    .description("Administrator role with full access")
                    .build();
            userRoleRepository.save(adminRole);
            log.info("ADMIN role created successfully");
        } else {
            log.info("ADMIN role already exists, skipping...");
        }

        if (!userRoleRepository.existsByName(UserRoleType.MANAGER)) {
            UserRole managerRole = UserRole.builder()
                    .name(UserRoleType.MANAGER)
                    .description("Moderates disaster post queue; assigned by administrators")
                    .build();
            userRoleRepository.save(managerRole);
            log.info("MANAGER role created successfully");
        } else {
            log.info("MANAGER role already exists, skipping...");
        }

        if (!userRoleRepository.existsByName(UserRoleType.BASIC)) {
            UserRole basicRole = UserRole.builder()
                    .name(UserRoleType.BASIC)
                    .description("Basic user role with limited access")
                    .build();
            userRoleRepository.save(basicRole);
            log.info("BASIC role created successfully");
        } else {
            log.info("BASIC role already exists, skipping...");
        }

        log.info("User roles seeding completed");
    }

    private void seedAdminUser() {
        log.info("Checking if admin user needs to be seeded...");
        
        if (!userRepository.existsByEmail(adminEmail)) {
            UserRole adminRole = userRoleRepository.findByName(UserRoleType.ADMIN)
                    .orElseThrow(() -> new RuntimeException("ADMIN role not found. Please ensure roles are seeded first."));

            User adminUser = User.builder()
                    .firstName(adminFirstName)
                    .lastName(adminLastName)
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(adminRole)
                    .isEmailVerified(true)
                    .build();

            userRepository.save(adminUser);
            log.info("Admin user created successfully");
            log.info("Admin credentials - Email: {}", adminEmail);
        } else {
            log.info("Admin user already exists, skipping...");
        }

        log.info("Admin user seeding completed");
    }

    private void seedManagerUser() {
        log.info("Checking if default manager user needs to be seeded...");

        if (!userRepository.existsByEmail(managerEmail)) {
            UserRole managerRole = userRoleRepository.findByName(UserRoleType.MANAGER)
                    .orElseThrow(() -> new RuntimeException("MANAGER role not found. Please ensure roles are seeded first."));

            User managerUser = User.builder()
                    .firstName(managerFirstName)
                    .lastName(managerLastName)
                    .email(managerEmail)
                    .password(passwordEncoder.encode(managerPassword))
                    .role(managerRole)
                    .isEmailVerified(true)
                    .build();

            userRepository.save(managerUser);
            log.info("Default manager user created successfully");
            log.info("Manager credentials - Email: {}", managerEmail);
        } else {
            log.info("Manager user already exists, skipping...");
        }

        log.info("Manager user seeding completed");
    }

    private void seedAbout() {
        log.info("Checking if About content needs to be seeded...");

        if (aboutRepository.count() == 0) {
            About about = About.builder()
                    .title("About Smart Disaster Hub")
                    .content(getDefaultAboutContent())
                    .build();
            aboutRepository.save(about);
            log.info("About content created successfully");
        } else {
            log.info("About content already exists, skipping...");
        }

        log.info("About content seeding completed");
    }

    private String getDefaultAboutContent() {
        return """
                <section class="about-section">
                  <h2>Our Mission</h2>
                  <p>
                    Smart Disaster Hub is an intelligent disaster detection and monitoring system designed to 
                    help communities and organizations stay informed about disaster-related events in real-time. 
                    Our platform leverages advanced machine learning algorithms to analyze social media content 
                    and identify disaster-related information, enabling faster response times and better 
                    preparedness.
                  </p>
                </section>

                <section class="about-section">
                  <h2>How It Works</h2>
                  <p>
                    The system continuously monitors Reddit posts from disaster-related communities, collecting 
                    and analyzing content using natural language processing and machine learning techniques. 
                    Each post is automatically evaluated for disaster relevance, categorized, and displayed 
                    on an interactive map that visualizes disaster activity by location.
                  </p>
                </section>

                <section class="about-section">
                  <h2>Key Features</h2>
                  <ul class="features-list">
                    <li>
                      <strong>Automated Content Collection:</strong> The system automatically fetches and processes 
                      Reddit posts every 15 minutes, ensuring you always have access to the latest disaster-related 
                      information.
                    </li>
                    <li>
                      <strong>AI-Powered Analysis:</strong> Advanced machine learning models analyze each post to 
                      determine disaster relevance and provide relevance scores, helping you identify the most 
                      critical information quickly.
                    </li>
                    <li>
                      <strong>Interactive Visualization:</strong> Explore disaster-related posts on an interactive 
                      map that shows activity by geographic location, making it easy to understand disaster patterns 
                      and trends.
                    </li>
                    <li>
                      <strong>Comprehensive Analytics:</strong> Access detailed statistics and filtering options 
                      to analyze disaster data, track trends, and generate insights that support decision-making.
                    </li>
                  </ul>
                </section>

                <section class="about-section">
                  <h2>Purpose</h2>
                  <p>
                    Smart Disaster Hub aims to bridge the gap between disaster information and those who need it most. 
                    By automating the collection and analysis of disaster-related content, we help emergency responders, 
                    government agencies, and communities make informed decisions quickly. Our platform transforms raw 
                    social media data into actionable intelligence, enabling faster response times and potentially 
                    saving lives.
                  </p>
                </section>

                <section class="about-section">
                  <h2>Technology</h2>
                  <p>
                    Built with modern web technologies and powered by machine learning, Smart Disaster Hub combines 
                    the reliability of traditional monitoring systems with the speed and reach of social media platforms. 
                    Our system processes information in real-time, ensuring that critical disaster information is 
                    available when it matters most.
                  </p>
                </section>
                """;
    }
}

