package com.caglamurat.smartDisasterHub.service.about;

import com.caglamurat.smartDisasterHub.domain.About;
import com.caglamurat.smartDisasterHub.dto.about.AboutDTO;
import com.caglamurat.smartDisasterHub.repository.IAboutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AboutService implements IAboutService {

    private final IAboutRepository aboutRepository;

    @Override
    @Transactional(readOnly = true)
    public AboutDTO getAbout() {
        Optional<About> aboutOpt = aboutRepository.findFirstByOrderByIdAsc();
        
        if (aboutOpt.isPresent()) {
            About about = aboutOpt.get();
            return AboutDTO.builder()
                    .id(about.getId())
                    .title(about.getTitle())
                    .content(about.getContent())
                    .createdAt(about.getCreatedAt())
                    .updatedAt(about.getUpdatedAt())
                    .build();
        }
        
        // Return default content if no record exists
        return AboutDTO.builder()
                .title("About Smart Disaster Hub")
                .content(getDefaultContent())
                .build();
    }

    @Override
    @Transactional
    public AboutDTO updateAbout(AboutDTO aboutDTO) {
        Optional<About> aboutOpt = aboutRepository.findFirstByOrderByIdAsc();
        
        About about;
        if (aboutOpt.isPresent()) {
            about = aboutOpt.get();
            about.setTitle(aboutDTO.getTitle());
            about.setContent(aboutDTO.getContent());
        } else {
            about = About.builder()
                    .title(aboutDTO.getTitle())
                    .content(aboutDTO.getContent())
                    .build();
        }
        
        About saved = aboutRepository.save(about);
        log.info("Saved/Updated About content: {}", saved.getId());
        
        return AboutDTO.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public AboutDTO createAbout(AboutDTO aboutDTO) {
        About about = About.builder()
                .title(aboutDTO.getTitle())
                .content(aboutDTO.getContent())
                .build();
        
        About saved = aboutRepository.save(about);
        log.info("Created About content: {}", saved.getId());
        
        return AboutDTO.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private String getDefaultContent() {
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





