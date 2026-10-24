import type { Project } from "../content/projects";

export function ProjectCard({ project }: { project: Project }) {
  return (
    <article className="card project-card" id={project.id}>
      <div className="cardTop">
        <h3 className="cardTitle">{project.title}</h3>
        <span className="status warn">{project.status}</span>
      </div>
      <p className="cardText">{project.summary}</p>
      <div className="chips">
        {project.tech.map((technology) => (
          <span className="chip" key={technology}>
            {technology}
          </span>
        ))}
      </div>
      <div className="linksRow project-links">
        {project.demo && <a href={project.demo}>Try sample demo →</a>}
        <a href={project.code}>
          {project.id === "tenmo" ? "Project overview" : "Source and README"} →
        </a>
      </div>
      <details className="case-study">
        <summary>Read the case study</summary>
        <h4>Problem</h4>
        <p>{project.problem}</p>
        <h4>Implementation</h4>
        <p>{project.work}</p>
        <h4>Current scope</h4>
        <p>{project.boundary}</p>
      </details>
    </article>
  );
}
